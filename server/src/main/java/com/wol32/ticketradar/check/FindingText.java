package com.wol32.ticketradar.check;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.CheckResponse.OfficialReference;
import com.wol32.ticketradar.event.EventResponse.PriceTierResponse;
import com.wol32.ticketradar.event.EventResponse.RuleResponse;

/**
 * 카드에 들어가는 문장 템플릿 (쉬운 말 설명, 판매글 구절 대체 문장, 공식 문장 요약).
 * AI 설명이 붙더라도 여기 문장이 기본값이고 판정은 바뀌지 않는다. 금지 단어와 안전을 단정하는 표현은 쓰지 않는다
 * (ForbiddenWordsTest 가 검사).
 */
final class FindingText {

    static final String TYPED = " (직접 입력)";
    static final String TRANSFER_QUOTE = "판매글 전체 (티켓을 양도하는 글)";

    private FindingText() {
    }

    // --- 판매글 구절: AI 근거가 있으면 그대로, 없으면 사용자가 입력한 값으로 문장을 만든다 ---

    static String priceQuote(Evidence evidence, int price) {
        return orTyped(evidence.pricePerTicket(), "1장당 " + won(price) + "원");
    }

    static String seatQuote(Evidence evidence, String seat) {
        return orTyped(evidence.seat(), seat == null || seat.isBlank() ? "좌석 등급 정보 없음" : "좌석: " + seat.strip());
    }

    static String quantityQuote(Evidence evidence, int quantity) {
        return orTyped(evidence.quantity(), quantity + "장");
    }

    static String deliveryQuote(Evidence evidence, DeliveryMethod method) {
        return orTyped(evidence.deliveryMethod(), "전달 방식: " + method.label());
    }

    static String prepaymentQuote(Evidence evidence) {
        return orTyped(evidence.prepaymentRequired(), "선입금 요구");
    }

    private static String orTyped(String quote, String typedFallback) {
        return quote == null || quote.isBlank() ? typedFallback + TYPED : quote.strip();
    }

    // --- 공식 문장 ---

    static String listPrice(PriceTierResponse tier) {
        return tier.grade() + " 정가 " + won(tier.price()) + "원";
    }

    /** 정가 대조 불가 카드의 공식 블록: 정가표 전체. 정가표가 비어 있으면 null (화면은 원문 링크만 둔다). */
    static OfficialReference priceTable(List<PriceTierResponse> tiers) {
        if (tiers.isEmpty()) {
            return null;
        }
        String table = tiers.stream().map(FindingText::listPrice).collect(Collectors.joining(" / "));
        return new OfficialReference("공식 정가표: " + table, tiers.get(0).sourceUrl(), tiers.get(0).checkedOn());
    }

    // --- 쉬운 말 설명 1~2문장 ---

    static String aboveList(int price, PriceTierResponse tier) {
        return "판매가 " + won(price) + "원은 " + tier.grade() + " 정가 " + won(tier.price()) + "원보다 "
                + won(price - tier.price()) + "원 높아요. 정가와 차이가 나는 이유를 판매자와 예매처에 확인해 보세요.";
    }

    static String notComparable(String seat, List<PriceTierResponse> tiers) {
        String table = tiers.isEmpty() ? "공식 정가표가 아직 등록되지 않았어요."
                : "공식 정가표에 있는 등급은 " + tiers.stream().map(PriceTierResponse::grade).collect(Collectors.joining(", ")) + " 예요.";
        if (seat == null || seat.isBlank()) {
            return "좌석 등급을 알 수 없어 공식 정가와 비교하지 못했어요. " + table;
        }
        return "'" + seat.strip() + "'에 해당하는 등급을 공식 정가표에서 찾지 못했어요. " + table;
    }

    static String prepayment() {
        return "판매글이 티켓을 받기 전에 먼저 입금하라고 해요. 예매처 밖에서 오간 돈은 예매처가 보장하지 않아요.";
    }

    static String deliveryConflict(DeliveryMethod method, RuleResponse rule) {
        return "판매글의 전달 방식은 '" + method.label() + "'이고, 공식 안내에는 '" + rule.type().label()
                + "' 규정이 있어요. 이 방법으로 받은 티켓으로 입장이 가능한지 예매처에 확인해 보세요.";
    }

    static String transferProhibited() {
        return "이 공연은 공식 안내에서 티켓 양도를 금지하고 있어요. 양도받은 티켓으로 입장이 가능한지 예매처에 확인해 보세요.";
    }

    static String quantityHigh(int quantity) {
        return "한 번에 " + quantity + "장을 팔고 있어요. 예매처의 1인 구매 한도와 맞는지 확인해 보세요.";
    }

    static String won(int amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
