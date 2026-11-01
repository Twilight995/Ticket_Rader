package com.wol32.ticketradar.extract;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.DeliveryMethod;

/** 계약서 2번 돌려주는 것. aiStatus 가 fallback 이면 값은 전부 null (오류가 아니다, HTTP 200). 연락처 칸은 없다. */
public record ExtractResponse(String aiStatus, Extracted extracted, Evidence evidence, String maskedText) {

    public static final String OK = "ok";
    public static final String FALLBACK = "fallback";

    public record Extracted(Integer pricePerTicket, String seat, Integer quantity, DeliveryMethod deliveryMethod,
            Boolean prepaymentRequired) {
    }

    static ExtractResponse ok(Extraction e, String maskedText) {
        Extraction safe = e.withEvidenceOrNone();
        return new ExtractResponse(OK, new Extracted(safe.pricePerTicket(), safe.seat(), safe.quantity(),
                safe.deliveryMethod(), safe.prepaymentRequired()), safe.evidence(), maskedText);
    }

    static ExtractResponse fallback(String maskedText) {
        return new ExtractResponse(FALLBACK, new Extracted(null, null, null, null, null), Evidence.NONE, maskedText);
    }
}
