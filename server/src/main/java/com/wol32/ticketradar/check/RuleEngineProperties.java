package com.wol32.ticketradar.check;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 규칙 엔진 기준값. 숫자는 코드가 아니라 application.properties 의 ticket-radar.rules.* 에 둔다.
 * priceCheckRatio: 정가 대비 이 배율 이상이면 "확인 필요", 그 미만(정가 초과)이면 "주의".
 * quantityHighThreshold: 이 수량 이상이면 "수량 많음" (정보).
 */
@ConfigurationProperties(prefix = "ticket-radar.rules")
public record RuleEngineProperties(double priceCheckRatio, int quantityHighThreshold) {
}
