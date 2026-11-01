package com.wol32.ticketradar.check;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.wol32.ticketradar.event.EventRepository;
import com.wol32.ticketradar.event.EventSeeder;

/** 계약서 3번 `POST /api/check` 를 실제 요청으로. 판단은 서비스가 하고 컨트롤러는 넘기기만 한다. (seed 공연 기준) */
@SpringBootTest
@AutoConfigureMockMvc
class CheckApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired EventRepository eventRepository;
    @Autowired EventSeeder eventSeeder;

    private long eventId;

    @BeforeEach
    void seed() throws Exception {
        eventRepository.deleteAll();
        eventSeeder.run(null);
        eventId = eventRepository.findFirstByOrderByIdAsc().orElseThrow().getId();
    }

    private ResultActions check(String json) throws Exception {
        return mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void demo_listing_returns_three_findings_summary_and_fixed_notice() throws Exception {
        String demo = """
                { "eventId": %d, "pricePerTicket": 250000, "seat": "VIP석 A구역", "quantity": 2,
                  "deliveryMethod": "CAPTURE_QR", "prepaymentRequired": true,
                  "evidence": { "pricePerTicket": "장당 25만", "seat": "VIP석 A구역", "quantity": "2장",
                                "deliveryMethod": "QR 캡처로 보내드려요", "prepaymentRequired": "선입금 확인 후" } }
                """.formatted(eventId);

        check(demo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.summary").value("확인이 필요한 부분 3가지를 찾았어요"))
                .andExpect(jsonPath("$.data.findings.length()").value(3))
                .andExpect(jsonPath("$.data.findings[0].type").value("PRICE_ABOVE_LIST"))
                .andExpect(jsonPath("$.data.findings[0].severity").value("CHECK"))
                .andExpect(jsonPath("$.data.findings[0].severityLabel").value("확인 필요"))
                .andExpect(jsonPath("$.data.findings[0].listingQuote").value("장당 25만"))
                .andExpect(jsonPath("$.data.findings[0].official.checkedOn").value("2026-10-28"))
                .andExpect(jsonPath("$.data.findings[1].type").value("DELIVERY_CONFLICTS_RULE"))
                .andExpect(jsonPath("$.data.findings[2].type").value("PREPAYMENT_REQUESTED"))
                .andExpect(jsonPath("$.data.findings[2].official").isEmpty())
                .andExpect(jsonPath("$.data.officialUrl").value(org.hamcrest.Matchers.startsWith("http")))
                .andExpect(jsonPath("$.data.notice").value("이 결과는 참고용이며 최종 판단은 사용자가 합니다."));
    }

    @Test
    void normal_listing_returns_empty_findings_and_no_safe_wording() throws Exception {
        check("""
                { "eventId": %d, "pricePerTicket": 143000, "seat": "R석", "quantity": 1, "deliveryMethod": "ON_SITE_PICKUP" }
                """.formatted(eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findings.length()").value(0))
                .andExpect(jsonPath("$.data.summary").value("확인이 필요한 부분을 찾지 못했어요. 거래 전 예매처 규정은 꼭 확인하세요"));
    }

    @Test
    void neither_price_nor_delivery_is_400() throws Exception {
        check("""
                { "eventId": %d, "seat": "VIP", "quantity": 1 }
                """.formatted(eventId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.pricePerTicket").value("1장당 가격이나 전달 방식 중 하나는 입력해 주세요"));
    }

    @Test
    void negative_price_zero_quantity_and_long_seat_are_400_with_fields() throws Exception {
        check("""
                { "eventId": %d, "pricePerTicket": -1, "quantity": 0, "seat": "%s" }
                """.formatted(eventId, "가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.pricePerTicket").value("0 이상의 숫자를 넣어 주세요"))
                .andExpect(jsonPath("$.error.fields.quantity").isString())
                .andExpect(jsonPath("$.error.fields.seat").isString());
    }

    @Test
    void unknown_delivery_method_and_text_price_are_400_naming_the_field() throws Exception {
        check("""
                { "eventId": %d, "pricePerTicket": 1000, "deliveryMethod": "FAX" }
                """.formatted(eventId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.deliveryMethod").value("전달 방식 5개 중 하나를 골라 주세요"));

        check("""
                { "eventId": %d, "pricePerTicket": "십만원" }
                """.formatted(eventId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.pricePerTicket").value("숫자만 입력해 주세요"));
    }

    @Test
    void unknown_event_is_404_with_korean_message() throws Exception {
        check("""
                { "eventId": 987654, "pricePerTicket": 1000 }
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.message").value("공연 정보를 찾을 수 없어요"));
    }
}
