package com.wol32.ticketradar.extract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.DeliveryMethod;

/**
 * Gemini 로 추출 (ADR 5, 1차 공급자). 의존성 없이 generateContent REST 를 직접 부른다.
 * 판매글은 &lt;listing&gt; 태그로 감싸 "분석할 자료"로만 다루게 하고, 출력은 JSON 스키마로 고정한다.
 * 어떤 실패든 예외로 던지면 ExtractService 가 fallback 으로 바꾼다.
 */
public class GeminiExtractor implements Extractor {

    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    static final String UNKNOWN = "UNKNOWN";
    static final int MAX_QUOTE_LENGTH = 120;
    static final int MAX_OUTPUT_TOKENS = 1024; // 정상 답은 200 토큰 안팎. 모델이 구절을 반복하며 폭주하면 빨리 끊기게
    static final String TRUNCATED = "MAX_TOKENS";

    private final RestClient restClient;
    private final AiProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    public GeminiExtractor(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public Extraction extract(ExtractionInput input) throws Exception {
        try {
            return parse(callRaw(input));
        } catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().contains(TRUNCATED)) {
                throw e;
            }
            // 모델이 가끔 근거 구절을 반복하다 한도에 걸린다(관찰: 4회 중 1회). 같은 요청을 한 번만 다시 보낸다.
            return parse(callRaw(input));
        }
    }

    /** 원본 응답 JSON. 연기 테스트가 실패 원인을 볼 때도 쓴다. */
    String callRaw(ExtractionInput input) {
        return restClient.post()
                // "model:generateContent" 만 주면 ':' 때문에 스킴으로 오해받으므로 전체 주소를 준다
                .uri(BASE_URL + properties.modelName() + ":generateContent")
                .header("x-goog-api-key", properties.geminiApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(input))
                .retrieve()
                .body(String.class);
    }

    // --- 요청 ---

    private Map<String, Object> requestBody(ExtractionInput input) {
        List<Object> parts = new ArrayList<>();
        parts.add(Map.of("text", ExtractionPrompt.build(input)));
        if (input.hasImage()) {
            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", input.imageMimeType(),
                    "data", Base64.getEncoder().encodeToString(input.imageBytes()))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        body.put("generationConfig", Map.of(
                "temperature", 0,
                "maxOutputTokens", MAX_OUTPUT_TOKENS,
                "responseMimeType", "application/json",
                "responseSchema", responseSchema()));
        return body;
    }

    private static Map<String, Object> responseSchema() {
        List<String> deliveryCodes = Stream.concat(
                Arrays.stream(DeliveryMethod.values()).map(Enum::name), Stream.of(UNKNOWN)).toList();
        Map<String, Object> evidenceProps = new LinkedHashMap<>();
        // seat 는 근거 칸을 두지 않는다: 좌석은 글에 적힌 대로 베끼는 값이라 그 자체가 근거이고,
        // 모델이 이 칸에서 글 전체를 반복하다 출력 한도에 걸리는 일이 잦았다 (2026-11-01 관찰)
        for (String field : List.of("pricePerTicket", "quantity", "deliveryMethod", "prepaymentRequired")) {
            evidenceProps.put(field, nullable("STRING"));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pricePerTicket", nullable("INTEGER"));
        props.put("seat", nullable("STRING"));
        props.put("quantity", nullable("INTEGER"));
        props.put("deliveryMethod", Map.of("type", "STRING", "enum", deliveryCodes));
        props.put("prepaymentRequired", nullable("BOOLEAN"));
        props.put("evidence", Map.of("type", "OBJECT", "properties", evidenceProps));
        return Map.of("type", "OBJECT", "properties", props, "required", List.copyOf(props.keySet()));
    }

    private static Map<String, Object> nullable(String type) {
        return Map.of("type", type, "nullable", true);
    }

    // --- 응답 ---

    /** Gemini 응답 JSON 안의 모델 답변(JSON 문자열)을 꺼내 Extraction 으로 바꾼다. 형식이 다르면 예외. */
    Extraction parse(String body) {
        JsonNode root = json.readTree(body);
        JsonNode candidate = root.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asString();
        if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
            throw new IllegalStateException("Gemini did not finish normally: " + finishReason);
        }
        if (!root.path("promptFeedback").path("blockReason").isMissingNode()) {
            throw new IllegalStateException("Gemini blocked the prompt");
        }
        JsonNode text = candidate.path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asString().isBlank()) {
            throw new IllegalStateException("Gemini answer has no text part");
        }
        Answer answer = json.readValue(text.asString(), Answer.class);
        Answer.Quotes q = answer.evidence() == null ? new Answer.Quotes(null, null, null, null) : answer.evidence();
        String seat = blankToNull(answer.seat());
        return new Extraction(answer.pricePerTicket(), seat, answer.quantity(),
                deliveryMethod(answer.deliveryMethod()), answer.prepaymentRequired(),
                new Evidence(blankToNull(q.pricePerTicket()), seat, blankToNull(q.quantity()),
                        blankToNull(q.deliveryMethod()), blankToNull(q.prepaymentRequired())));
    }

    private static DeliveryMethod deliveryMethod(String code) {
        if (code == null || UNKNOWN.equals(code)) {
            return null;
        }
        return Arrays.stream(DeliveryMethod.values()).filter(m -> m.name().equals(code)).findFirst().orElse(null);
    }

    /** 빈 값은 null, 모델이 베껴 넣은 태그 조각은 제거, 근거 구절은 짧게 자른다 (카드에 한 줄로 보여 줄 값). */
    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String cleaned = s.replace("</listing>", "").replace("<listing>", "").strip();
        if (cleaned.isBlank()) {
            return null;
        }
        return cleaned.length() > MAX_QUOTE_LENGTH ? cleaned.substring(0, MAX_QUOTE_LENGTH) + "…" : cleaned;
    }

    /** 모델이 돌려주는 JSON 모양. 모르는 칸은 무시한다. */
    record Answer(Integer pricePerTicket, String seat, Integer quantity, String deliveryMethod,
            Boolean prepaymentRequired, Quotes evidence) {
        record Quotes(String pricePerTicket, String quantity, String deliveryMethod, String prepaymentRequired) {
        }
    }
}
