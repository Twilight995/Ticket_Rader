package com.wol32.ticketradar.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.DeliveryMethod;
import com.wol32.ticketradar.event.EventRepository;
import com.wol32.ticketradar.event.EventSeeder;

/**
 * 계약서 2번 `POST /api/extract`. AI 는 가짜(Extractor mock)로 끼운다.
 * 시간 초과 1초, 접속별 분당 3회로 낮춰서 한도 동작을 빨리 확인한다.
 */
@SpringBootTest(properties = {
        "ticket-radar.ai.timeout-seconds=1",
        "ticket-radar.ai.max-requests-per-minute-per-ip=3",
        "ticket-radar.ai.max-requests-per-minute-total=100" })
@AutoConfigureMockMvc
class ExtractApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired MockMvc mockMvc;
    @Autowired EventRepository eventRepository;
    @Autowired EventSeeder eventSeeder;
    @MockitoBean Extractor extractor;

    private static final java.util.concurrent.atomic.AtomicInteger IP_COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    private long eventId;

    @BeforeEach
    void seed() throws Exception {
        eventRepository.deleteAll();
        eventSeeder.run(null);
        eventId = eventRepository.findFirstByOrderByIdAsc().orElseThrow().getId();
    }

    private MockMultipartHttpServletRequestBuilder extractRequest() {
        int ip = IP_COUNTER.incrementAndGet(); // 테스트마다 다른 접속으로 보여 접속별 한도가 섞이지 않게
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/extract")
                .with(request -> {
                    request.setRemoteAddr("10.0.0." + ip);
                    return request;
                });
    }

    private static Extraction demoExtraction() {
        return new Extraction(250000, "VIP석 A구역", 2, DeliveryMethod.CAPTURE_QR, true,
                new Evidence("장당 25만", "VIP석 A구역", "2장", "QR 캡처로 보내드려요", "선입금 확인 후"));
    }

    // --- 입력 검증 (점검 ③) ---

    @Test
    void neither_text_nor_image_is_400() throws Exception {
        mockMvc.perform(extractRequest().param("eventId", String.valueOf(eventId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.text").value("판매글이나 캡처 이미지 중 하나는 넣어 주세요"));
    }

    @Test
    void text_over_5000_chars_is_400() throws Exception {
        mockMvc.perform(extractRequest().param("eventId", String.valueOf(eventId)).param("text", "가".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.text").value("판매글은 5,000자 이하로 넣어 주세요"));
    }

    @Test
    void text_file_named_png_is_400() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("image", "shot.png", "image/png",
                "this is text".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(extractRequest().file(fake).param("eventId", String.valueOf(eventId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.image").value("PNG 또는 JPG 이미지만 올릴 수 있어요"));
    }

    @Test
    void missing_event_id_is_400_and_unknown_event_is_404() throws Exception {
        mockMvc.perform(extractRequest().param("text", "VIP 2장"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.eventId").isString());
        mockMvc.perform(extractRequest().param("eventId", "987654").param("text", "VIP 2장"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("공연 정보를 찾을 수 없어요"));
    }

    // --- 성공: 마스킹 후 AI 에 보내고, 연락처는 응답 어디에도 없다 ---

    @Test
    void text_is_masked_before_extraction_and_contacts_never_come_back() throws Exception {
        given(extractor.extract(any())).willReturn(demoExtraction());
        String listing = "VIP석 A구역 2장 양도. 장당 25만. 선입금 확인 후 QR 캡처로 보내드려요. 연락 010-0000-1234 카톡 아이디 fake_id";

        mockMvc.perform(extractRequest().param("eventId", String.valueOf(eventId)).param("text", listing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.aiStatus").value("ok"))
                .andExpect(jsonPath("$.data.extracted.pricePerTicket").value(250000))
                .andExpect(jsonPath("$.data.extracted.deliveryMethod").value("CAPTURE_QR"))
                .andExpect(jsonPath("$.data.extracted.prepaymentRequired").value(true))
                .andExpect(jsonPath("$.data.evidence.pricePerTicket").value("장당 25만"))
                .andExpect(jsonPath("$.data.maskedText").value(containsString("[삭제]")))
                .andExpect(jsonPath("$.data.contacts").doesNotExist())
                .andExpect(content().string(not(containsString("0000-1234"))))
                .andExpect(content().string(not(containsString("fake_id"))));

        ArgumentCaptor<ExtractionInput> sent = ArgumentCaptor.forClass(ExtractionInput.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue().maskedText()).doesNotContain("0000-1234", "fake_id").contains("장당 25만");
        assertThat(sent.getValue().seatGrades()).contains("VIP", "R", "S");
    }

    @Test
    void image_only_request_passes_bytes_and_mime_and_returns_null_masked_text() throws Exception {
        given(extractor.extract(any())).willReturn(demoExtraction());
        MockMultipartFile png = new MockMultipartFile("image", "shot.png", "image/png", PNG);

        mockMvc.perform(extractRequest().file(png).param("eventId", String.valueOf(eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiStatus").value("ok"))
                .andExpect(jsonPath("$.data.maskedText").isEmpty());

        ArgumentCaptor<ExtractionInput> sent = ArgumentCaptor.forClass(ExtractionInput.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue().imageMimeType()).isEqualTo("image/png");
        assertThat(sent.getValue().imageBytes()).isEqualTo(PNG);
    }

    @Test
    void contacts_inside_ai_output_are_masked_too() throws Exception {
        given(extractor.extract(any())).willReturn(new Extraction(250000, "VIP석 (문의 010-0000-7777)", 2,
                DeliveryMethod.CAPTURE_QR, true,
                new Evidence("장당 25만", null, null, "QR 전송", "선입금 후 010-0000-7777 연락")));
        MockMultipartFile png = new MockMultipartFile("image", "shot.png", "image/png", PNG);

        mockMvc.perform(extractRequest().file(png).param("eventId", String.valueOf(eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extracted.seat").value(containsString("[삭제]")))
                .andExpect(jsonPath("$.data.evidence.prepaymentRequired").value(containsString("[삭제]")))
                .andExpect(content().string(not(containsString("0000-7777"))));
    }

    // --- AI 실패는 오류가 아니다: 200 + fallback + 빈 값 ---

    @Test
    void extractor_exception_is_200_fallback_with_empty_values() throws Exception {
        given(extractor.extract(any())).willThrow(new IllegalStateException("provider down: key sk-fake at /srv"));

        mockMvc.perform(extractRequest().param("eventId", String.valueOf(eventId)).param("text", "VIP 2장 25만"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.aiStatus").value("fallback"))
                .andExpect(jsonPath("$.data.extracted.pricePerTicket").isEmpty())
                .andExpect(jsonPath("$.data.extracted.deliveryMethod").isEmpty())
                .andExpect(jsonPath("$.data.evidence.pricePerTicket").isEmpty())
                .andExpect(jsonPath("$.data.maskedText").value("VIP 2장 25만"))
                .andExpect(content().string(not(containsString("sk-fake"))));
    }

    @Test
    void extractor_slower_than_timeout_is_200_fallback() throws Exception {
        given(extractor.extract(any())).willAnswer(invocation -> {
            Thread.sleep(2500);
            return demoExtraction();
        });

        mockMvc.perform(extractRequest().param("eventId", String.valueOf(eventId)).param("text", "VIP 2장 25만"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiStatus").value("fallback"));
    }

    // --- 같은 접속 분당 한도 (AI 비용·무료 한도 보호) ---

    @Test
    void same_ip_over_the_per_minute_limit_is_429() throws Exception {
        given(extractor.extract(any())).willReturn(demoExtraction());
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(multipart("/api/extract").with(r -> {
                r.setRemoteAddr("10.9.9.9");
                return r;
            }).param("eventId", String.valueOf(eventId)).param("text", "VIP 2장")).andExpect(status().isOk());
        }

        mockMvc.perform(multipart("/api/extract").with(r -> {
            r.setRemoteAddr("10.9.9.9");
            return r;
        }).param("eventId", String.valueOf(eventId)).param("text", "VIP 2장"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message").value("요청이 너무 많아요. 잠시 후 다시 시도해 주세요"));
    }
}
