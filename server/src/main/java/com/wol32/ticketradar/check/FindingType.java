package com.wol32.ticketradar.check;

/** 확인사항 종류 6개. 종류당 카드 1장. 선언 순서가 같은 심각도 안에서의 정렬 순서다 (계약서 0절 표). */
public enum FindingType {
    PRICE_ABOVE_LIST("정가보다 높음"),
    PRICE_NOT_COMPARABLE("정가 대조 불가"),
    PREPAYMENT_REQUESTED("선입금 요구"),
    DELIVERY_CONFLICTS_RULE("전달 방식이 규정과 충돌"),
    TRANSFER_PROHIBITED("양도 금지 공연"),
    QUANTITY_HIGH("수량 많음");

    private final String title;

    FindingType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
