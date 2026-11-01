package com.wol32.ticketradar.extract;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.common.ApiException;
import com.wol32.ticketradar.common.ApiExceptionHandler;
import com.wol32.ticketradar.event.EventResponse;
import com.wol32.ticketradar.event.EventResponse.PriceTierResponse;
import com.wol32.ticketradar.event.EventService;

/**
 * 계약서 2번. 순서: 입력 확인 → 분당 한도 → 공연 확인 → 연락처 마스킹 → AI(시간 제한) → 응답.
 * AI 실패·시간 초과·형식 불일치는 오류가 아니라 fallback (HTTP 200). 원문·이미지는 어디에도 저장하지 않는다.
 */
@Service
public class ExtractService {

    static final int MAX_TEXT_LENGTH = 5_000;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final String TEXT_OR_IMAGE_REQUIRED = "판매글이나 캡처 이미지 중 하나는 넣어 주세요";
    static final String TEXT_TOO_LONG = "판매글은 5,000자 이하로 넣어 주세요";
    static final String IMAGE_TYPE = "PNG 또는 JPG 이미지만 올릴 수 있어요";
    static final String IMAGE_TOO_LARGE = "이미지는 5MB 이하여야 해요";
    static final String IMAGE_UNREADABLE = "이미지를 읽지 못했어요. 다른 이미지를 올려 주세요";
    static final String EVENT_REQUIRED = "공연을 선택해 주세요";
    static final String EVENT_NOT_FOUND = "공연 정보를 찾을 수 없어요";
    static final String TOO_MANY = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요";

    private static final Logger log = LoggerFactory.getLogger(ExtractService.class);

    private final EventService eventService;
    private final Extractor extractor;
    private final RateLimiter rateLimiter;
    private final AiProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ExtractService(EventService eventService, Extractor extractor, RateLimiter rateLimiter,
            AiProperties properties) {
        this.eventService = eventService;
        this.extractor = extractor;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public ExtractResponse extract(Long eventId, String text, MultipartFile image, String clientIp) {
        byte[] imageBytes = readImage(image);
        validate(eventId, text, imageBytes);
        enforceRateLimit(clientIp);
        EventResponse event = eventService.findEvent(eventId)
                .orElseThrow(() -> ApiException.notFound(EVENT_NOT_FOUND));

        String maskedText = text == null || text.isBlank() ? null : ContactMasker.mask(text);
        String mime = imageBytes == null ? null : ImageSniffer.detect(imageBytes).orElse(null);
        List<String> grades = event.priceTiers().stream().map(PriceTierResponse::grade).toList();
        ExtractionInput input = new ExtractionInput(maskedText, imageBytes, mime, grades);

        return runWithTimeout(input)
                .map(extraction -> ExtractResponse.ok(maskContacts(extraction), maskedText))
                .orElseGet(() -> ExtractResponse.fallback(maskedText));
    }

    /** AI 가 돌려준 구절에도 연락처가 섞일 수 있다 (특히 이미지 입력). 응답 전에 한 번 더 지운다. */
    static Extraction maskContacts(Extraction e) {
        Extraction safe = e.withEvidenceOrNone();
        Evidence q = safe.evidence();
        return new Extraction(safe.pricePerTicket(), ContactMasker.mask(safe.seat()), safe.quantity(),
                safe.deliveryMethod(), safe.prepaymentRequired(),
                new Evidence(ContactMasker.mask(q.pricePerTicket()), ContactMasker.mask(q.seat()),
                        ContactMasker.mask(q.quantity()), ContactMasker.mask(q.deliveryMethod()),
                        ContactMasker.mask(q.prepaymentRequired())));
    }

    private static byte[] readImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest(ApiExceptionHandler.INVALID_MESSAGE, Map.of("image", IMAGE_TOO_LARGE));
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest(ApiExceptionHandler.INVALID_MESSAGE, Map.of("image", IMAGE_UNREADABLE));
        }
    }

    private static void validate(Long eventId, String text, byte[] imageBytes) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (eventId == null) {
            fields.put("eventId", EVENT_REQUIRED);
        }
        boolean hasText = text != null && !text.isBlank();
        if (!hasText && imageBytes == null) {
            fields.put("text", TEXT_OR_IMAGE_REQUIRED);
            fields.put("image", TEXT_OR_IMAGE_REQUIRED);
        }
        if (hasText && text.length() > MAX_TEXT_LENGTH) {
            fields.put("text", TEXT_TOO_LONG);
        }
        if (imageBytes != null && ImageSniffer.detect(imageBytes).isEmpty()) {
            fields.put("image", IMAGE_TYPE);
        }
        if (!fields.isEmpty()) {
            throw ApiException.badRequest(ApiExceptionHandler.INVALID_MESSAGE, fields);
        }
    }

    private void enforceRateLimit(String clientIp) {
        boolean allowed = rateLimiter.tryAcquire("ip:" + clientIp, properties.maxRequestsPerMinutePerIp())
                && rateLimiter.tryAcquire("total", properties.maxRequestsPerMinuteTotal());
        if (!allowed) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY, null);
        }
    }

    /** AI 호출을 제한 시간 안에서만 기다린다. 실패 이유는 종류만 로그에 남기고 원문·키·메시지는 남기지 않는다. */
    private Optional<Extraction> runWithTimeout(ExtractionInput input) {
        Future<Extraction> future = executor.submit(() -> extractor.extract(input));
        try {
            return Optional.ofNullable(future.get(properties.timeoutSeconds(), TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("AI extraction fell back: {}", cause.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            future.cancel(true); // 끝났으면 아무 일 없고, 아직 돌고 있으면 멈춘다
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
