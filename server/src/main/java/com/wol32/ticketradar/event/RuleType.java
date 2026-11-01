package com.wol32.ticketradar.event;

/** 규정 종류 5개 (화면 명세표 ⑤). 본인 확인 필수 / 양도 금지 / 캡처 입장 불가 / 현장 수령 필수 / 기타 */
public enum RuleType {
    IDENTITY_CHECK_REQUIRED,
    TRANSFER_PROHIBITED,
    CAPTURE_ENTRY_NOT_ALLOWED,
    ON_SITE_PICKUP_REQUIRED,
    OTHER
}
