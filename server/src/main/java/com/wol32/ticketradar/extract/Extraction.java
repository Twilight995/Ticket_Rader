package com.wol32.ticketradar.extract;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.DeliveryMethod;

/** AI 가 뽑은 값. 못 읽은 칸은 null. evidence 는 각 값을 읽은 판매글 구절. */
public record Extraction(
        Integer pricePerTicket,
        String seat,
        Integer quantity,
        DeliveryMethod deliveryMethod,
        Boolean prepaymentRequired,
        Evidence evidence) {

    static final Extraction EMPTY = new Extraction(null, null, null, null, null, Evidence.NONE);

    Extraction withEvidenceOrNone() {
        return evidence == null ? new Extraction(pricePerTicket, seat, quantity, deliveryMethod, prepaymentRequired,
                Evidence.NONE) : this;
    }
}
