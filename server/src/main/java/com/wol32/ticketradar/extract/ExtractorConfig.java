package com.wol32.ticketradar.extract;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 환경 변수로 AI 공급자를 고른다 (ADR 5). 키가 없으면 none: 서버는 뜨고 추출은 항상 fallback. */
@Configuration
public class ExtractorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExtractorConfig.class);

    @Bean
    Extractor extractor(AiProperties properties, ObjectProvider<RestClient.Builder> builders) {
        switch (properties.provider()) {
            case "gemini" -> {
                if (!properties.hasGeminiKey() || !properties.hasModel()) {
                    log.warn("AI_PROVIDER=gemini but GEMINI_API_KEY or AI_MODEL is empty: extraction will always fall back");
                    return new NoneExtractor();
                }
                log.info("AI extraction: Gemini, model {}", properties.model());
                return new GeminiExtractor(withTimeouts(builders.getIfAvailable(RestClient::builder), properties), properties);
            }
            case "claude" -> {
                log.warn("AI_PROVIDER=claude is not implemented yet (ADR 5): extraction will always fall back");
                return new NoneExtractor();
            }
            default -> {
                log.info("AI extraction is off (AI_PROVIDER=none): the flow continues with manual input");
                return new NoneExtractor();
            }
        }
    }

    private static RestClient.Builder withTimeouts(RestClient.Builder builder, AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        return builder.requestFactory(factory);
    }
}
