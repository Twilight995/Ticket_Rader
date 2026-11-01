package com.wol32.ticketradar.check;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wol32.ticketradar.check.CheckResponse.Finding;
import com.wol32.ticketradar.common.ApiException;
import com.wol32.ticketradar.common.ApiExceptionHandler;
import com.wol32.ticketradar.event.EventResponse;
import com.wol32.ticketradar.event.EventService;

/** 계약서 3번: 입력 규칙 확인 → 공연 읽기 → 규칙 엔진 → 응답. 저장하지 않는다 (ADR 2). */
@Service
public class CheckService {

    static final String EVENT_NOT_FOUND = "공연 정보를 찾을 수 없어요";

    private final EventService eventService;
    private final RuleEngine ruleEngine;

    public CheckService(EventService eventService, RuleEngine ruleEngine) {
        this.eventService = eventService;
        this.ruleEngine = ruleEngine;
    }

    public CheckResponse check(CheckRequest request) {
        if (!request.hasPriceOrDelivery()) {
            throw ApiException.badRequest(ApiExceptionHandler.INVALID_MESSAGE, Map.of(
                    "pricePerTicket", CheckRequest.PRICE_OR_DELIVERY_REQUIRED,
                    "deliveryMethod", CheckRequest.PRICE_OR_DELIVERY_REQUIRED));
        }
        EventResponse event = eventService.findEvent(request.eventId())
                .orElseThrow(() -> ApiException.notFound(EVENT_NOT_FOUND));
        List<Finding> findings = ruleEngine.evaluate(request, event);
        return new CheckResponse(CheckResponse.summaryFor(findings.size()), findings, event.officialUrl(),
                CheckResponse.NOTICE);
    }
}
