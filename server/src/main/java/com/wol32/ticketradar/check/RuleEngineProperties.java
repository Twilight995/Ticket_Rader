package com.wol32.ticketradar.check;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 규칙 엔진 기준값. 숫자는 코드가 아니라 application.properties 의 ticket-radar.rules.* 에 둔다.
 * priceCheckRatio: 정가 대비 이 배율 이상이면 "확인 필요", 그 미만(정가 초과)이면 "주의".
 * quantityHighThreshold: 이 수량 이상이면 "수량 많음" (정보).
 */
@ConfigurationProperties(prefix = "ticket-radar.rules")
@Validated
public record RuleEngineProperties(
        @DecimalMin(value = "1.0", message = "ticket-radar.rules.price-check-ratio must be at least 1.0") double priceCheckRatio,
        @Min(value = 1, message = "ticket-radar.rules.quantity-high-threshold must be at least 1") int quantityHighThreshold) {
}
