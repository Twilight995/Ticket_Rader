package com.wol32.ticketradar.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * 실제 Gemini 를 한 번 부르는 연기 테스트. GEMINI_API_KEY 와 AI_MODEL 환경 변수가 있을 때만 돈다
 * (보통 테스트에서는 건너뜀). 가상 글만 보낸다. 실행: GEMINI_API_KEY=... AI_MODEL=... ./mvnw test -Dtest=GeminiLiveSmokeTest
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiLiveSmokeTest {

    @Test
    void extracts_the_demo_listing_with_the_real_api() throws Exception {
        String model = System.getenv().getOrDefault("AI_MODEL", "gemini-3.5-flash-lite");
        AiProperties props = new AiProperties("gemini", model, System.getenv("GEMINI_API_KEY"), "", 20, 10, 14);
        GeminiExtractor extractor = new GeminiExtractor(RestClient.builder(), props);
        String masked = "2026 OO 콘서트 VIP석 A구역 2장 양도해요. 장당 25만. 선입금 확인 후 QR 캡처로 보내드려요. 연락 [삭제]";

        ExtractionInput input = new ExtractionInput(masked, null, null, List.of("VIP", "R", "S"));
        String raw = extractor.callRaw(input);
        Extraction result;
        try {
            result = extractor.parse(raw);
        } catch (Exception e) {
            // 실패 원인을 보기 위한 출력. 가상 글이라 개인정보가 없다.
            System.out.println("RAW GEMINI RESPONSE (fictional listing): " + raw.replace('\n', ' ').replace('\r', ' '));
            throw e;
        }

        assertThat(result.pricePerTicket()).isEqualTo(250000);
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.prepaymentRequired()).isTrue();
        assertThat(result.evidence().pricePerTicket()).isNotBlank();
    }
}
