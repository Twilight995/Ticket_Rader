package com.wol32.ticketradar.check;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 계약서 3번 돌려주는 것. findings 는 0개 이상, 종류당 최대 1개, 심각도 순. */
public record CheckResponse(String summary, List<Finding> findings, String officialUrl, String notice) {

    public static final String NOTICE = "이 결과는 참고용이며 최종 판단은 사용자가 합니다.";
    public static final String NONE_FOUND = "확인이 필요한 부분을 찾지 못했어요. 거래 전 예매처 규정은 꼭 확인하세요";

    /** 카드 한 장. official 은 대응하는 공식 문장이 없는 종류(선입금·수량)에서 null. */
    public record Finding(
            FindingType type,
            String title,
            Severity severity,
            String severityLabel,
            String listingQuote,
            OfficialReference official,
            String explanation,
            Map<String, Object> details) {

        static Finding of(FindingType type, Severity severity, String listingQuote, OfficialReference official,
                String explanation, Map<String, Object> details) {
            return new Finding(type, type.title(), severity, severity.label(), listingQuote, official, explanation,
                    details);
        }
    }

    /** 공식 문장 + 출처 URL + 확인 날짜. 정가표 행 또는 규정 행에서 온다. */
    public record OfficialReference(String text, String sourceUrl, LocalDate checkedOn) {
    }

    static String summaryFor(int count) {
        return count == 0 ? NONE_FOUND : "확인이 필요한 부분 " + count + "가지를 찾았어요";
    }
}
