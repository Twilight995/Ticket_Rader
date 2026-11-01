package com.wol32.ticketradar.extract;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 연락처를 "[삭제]" 로 바꾼다 (절대 규칙 6). AI 에 보내기 전의 글과, AI 가 돌려준 구절 둘 다에 쓴다.
 * 지우는 것: 전화번호, 계좌번호, 오픈채팅·SNS 링크, 메신저 아이디("카톡 abc", "인스타 abc"), @아이디, 이메일.
 * 지우지 않는 것: 날짜(2026-12-20), 시각(19:00), 가격(150,000원), 좌석(A구역 12열), 수량(2장), 대표번호(1588-1234).
 */
final class ContactMasker {

    static final String MASK = "[삭제]";

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** 항상 지우는 것 (순서대로 적용). */
    private static final List<Pattern> ALWAYS = List.of(
            // 메신저·SNS 아이디: "카톡 아이디 abc", "카카오톡 ID: abc", "오픈카톡 abc", "인스타 abc_1", "텔레그램 아이디 abc"
            Pattern.compile("(?i)(카카오톡|카톡|카카오|오픈카톡|오카|인스타그램|인스타|텔레그램|텔레|라인|디스코드|DM)"
                    + "\\s*(아이디|ID)?\\s*[:：]?\\s*[A-Za-z0-9._-]{3,}"),
            // 오픈채팅·텔레그램·SNS 링크
            Pattern.compile("(?i)(https?://)?(www\\.)?(open\\.kakao\\.com|t\\.me|telegram\\.me|instagram\\.com|band\\.us"
                    + "|line\\.me|discord\\.gg|x\\.com|twitter\\.com)/\\S+"),
            // 이메일
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            // @아이디
            Pattern.compile("@[A-Za-z0-9_]{3,}"),
            // 국제 표기 전화번호
            Pattern.compile("\\+82\\s*[-.]?\\s*\\d{1,2}\\s*[-.]?\\s*\\d{3,4}\\s*[-.]?\\s*\\d{4}"),
            // 국내 전화번호 (0으로 시작, 붙여 쓰기·하이픈 주변 공백 포함, 지역번호 02-123-4567 포함)
            Pattern.compile("(?<!\\d)0\\d{1,2}\\s*[-.]?\\s*\\d{3,4}\\s*[-.]?\\s*\\d{4}(?!\\d)"),
            // "계좌/입금 (은행) 3333 01 1234567" 처럼 공백으로 나눈 계좌번호
            Pattern.compile("(?<=계좌|입금)\\s*[:：]?\\s*(?:[가-힣]{2,4}\\s*)?\\d[\\d\\s-]{8,}\\d"),
            // 10자리 이상 연속 숫자 (붙여 쓴 전화번호·계좌번호)
            Pattern.compile("(?<![\\d,])\\d{10,}(?![\\d,])"));

    /** 하이픈 숫자 묶음 (계좌번호). 날짜 모양(2026-12-20)만 남긴다. */
    private static final Pattern HYPHENATED_DIGITS =
            Pattern.compile("(?<!\\d)\\d{2,6}-\\d{2,6}-\\d{2,8}(?:-\\d{2,8})?(?!\\d)");

    private ContactMasker() {
    }

    static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = Normalizer.normalize(text, Normalizer.Form.NFKC); // 전각 숫자 ０１０ → 010
        for (Pattern pattern : ALWAYS) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        return HYPHENATED_DIGITS.matcher(result)
                .replaceAll(m -> DATE.matcher(m.group()).matches() ? m.group() : MASK);
    }
}
