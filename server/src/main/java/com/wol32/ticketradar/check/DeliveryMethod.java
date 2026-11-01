package com.wol32.ticketradar.check;

/** 전달 방식 5개 (화면 명세표 ②). 서버는 코드를 주고받고, 한국어 이름은 판매글 구절 대체 문장에만 쓴다. */
public enum DeliveryMethod {
    ON_SITE_PICKUP("본인 현장 수령"),
    CAPTURE_QR("캡처·QR 전송"),
    ACCOUNT_TRANSFER("계정 양도"),
    MOBILE_TICKET_TRANSFER("모바일 티켓 전송"),
    OTHER("기타");

    private final String label;

    DeliveryMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
