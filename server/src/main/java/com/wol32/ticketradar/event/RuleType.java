package com.wol32.ticketradar.event;

/** 규정 종류 5개 (화면 명세표 ⑤). 한국어 이름은 확인사항 설명 문장에 쓴다. */
public enum RuleType {
    IDENTITY_CHECK_REQUIRED("본인 확인 필수"),
    TRANSFER_PROHIBITED("양도 금지"),
    CAPTURE_ENTRY_NOT_ALLOWED("캡처 입장 불가"),
    ON_SITE_PICKUP_REQUIRED("현장 수령 필수"),
    OTHER("기타");

    private final String label;

    RuleType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
