package com.wol32.ticketradar.check;

/** 심각도 3단계. 화면은 색과 함께 반드시 글자 라벨을 보여 준다. 정렬은 확인 필요 → 주의 → 정보. */
public enum Severity {
    CHECK("확인 필요"),
    CAUTION("주의"),
    INFO("정보");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
