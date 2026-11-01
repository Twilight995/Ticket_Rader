package com.wol32.ticketradar.extract;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 추출 설정 (ADR 5). 값은 .env → application.properties(ticket-radar.ai.*) 로 들어온다.
 * provider: none | gemini | claude. 키가 비어 있으면 none 과 같다.
 * 분당 한도는 접속(IP)별과 전체 두 겹: 무료 티어 한도(프로젝트 전체 분당 15회)를 넘지 않게.
 */
@ConfigurationProperties(prefix = "ticket-radar.ai")
@Validated
public record AiProperties(
        @Pattern(regexp = "none|gemini|claude", message = "ticket-radar.ai.provider must be none, gemini or claude") String provider,
        String model,
        String geminiApiKey,
        String anthropicApiKey,
        @Min(value = 1, message = "ticket-radar.ai.timeout-seconds must be at least 1") int timeoutSeconds,
        @Min(value = 1, message = "ticket-radar.ai.max-requests-per-minute-per-ip must be at least 1") int maxRequestsPerMinutePerIp,
        @Min(value = 1, message = "ticket-radar.ai.max-requests-per-minute-total must be at least 1") int maxRequestsPerMinuteTotal) {

    boolean hasGeminiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    boolean hasModel() {
        return model != null && !model.isBlank();
    }

    /** "models/gemini-x" 라고 적어도 "gemini-x" 로. */
    String modelName() {
        String m = model == null ? "" : model.strip();
        return m.startsWith("models/") ? m.substring("models/".length()) : m;
    }

    /** 키가 로그·오류 메시지에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "AiProperties[provider=" + provider + ", model=" + model + ", geminiApiKey=" + (hasGeminiKey() ? "***" : "")
                + ", anthropicApiKey=" + (anthropicApiKey == null || anthropicApiKey.isBlank() ? "" : "***")
                + ", timeoutSeconds=" + timeoutSeconds + "]";
    }
}
