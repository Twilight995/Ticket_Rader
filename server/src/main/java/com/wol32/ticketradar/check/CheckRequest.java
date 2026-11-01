package com.wol32.ticketradar.check;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계약서 3번 `POST /api/check` 의 보내는 것. 화면이 막았어도 서버가 다시 확인한다. */
public record CheckRequest(
        @NotNull(message = "공연을 선택해 주세요") Long eventId,
        @Min(value = 0, message = "0 이상의 숫자를 넣어 주세요") Integer pricePerTicket,
        @Size(max = 100, message = "100자 이하로 입력해 주세요") String seat,
        @Min(value = 1, message = "1 이상의 숫자를 넣어 주세요") @Max(value = 100, message = "100 이하의 숫자를 넣어 주세요") Integer quantity,
        DeliveryMethod deliveryMethod,
        Boolean prepaymentRequired,
        @Valid Evidence evidence) {

    static final String PRICE_OR_DELIVERY_REQUIRED = "1장당 가격이나 전달 방식 중 하나는 입력해 주세요";
    private static final String QUOTE_TOO_LONG = "300자 이하여야 해요";

    /** AI가 각 값을 읽은 판매글 구절. 사용자가 고친 칸은 null 로 온다. */
    public record Evidence(
            @Size(max = 300, message = QUOTE_TOO_LONG) String pricePerTicket,
            @Size(max = 300, message = QUOTE_TOO_LONG) String seat,
            @Size(max = 300, message = QUOTE_TOO_LONG) String quantity,
            @Size(max = 300, message = QUOTE_TOO_LONG) String deliveryMethod,
            @Size(max = 300, message = QUOTE_TOO_LONG) String prepaymentRequired) {

        public static final Evidence NONE = new Evidence(null, null, null, null, null);
    }

    Evidence evidenceOrNone() {
        return evidence == null ? Evidence.NONE : evidence;
    }

    boolean hasPriceOrDelivery() {
        return pricePerTicket != null || deliveryMethod != null;
    }
}
