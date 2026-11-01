package com.wol32.ticketradar.check;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.CheckResponse.Finding;
import com.wol32.ticketradar.check.CheckResponse.OfficialReference;
import com.wol32.ticketradar.event.EventResponse;
import com.wol32.ticketradar.event.EventResponse.PriceTierResponse;
import com.wol32.ticketradar.event.EventResponse.RuleResponse;
import com.wol32.ticketradar.event.EventService;
import com.wol32.ticketradar.event.RuleType;

/**
 * 판단은 여기서, 코드로 한다 (절대 규칙 2). 순수 함수: 입력 + 공연 + 기준값만 보고, 저장·시간·난수 없이,
 * 같은 입력이면 항상 같은 결과를 돌려준다. 종류당 카드 1장, 심각도(확인 필요 → 주의 → 정보) 다음 종류 순으로 정렬.
 */
@Component
public class RuleEngine {

    /**
     * 전달 방식 → 정말로 어긋나는 규정 종류, 가장 직접적인 것부터. 첫 번째로 공연에 있는 규정 하나만 카드가 된다.
     * "본인 확인 필수"는 계정 양도와만 짝짓는다 (캡처·모바일 전송은 입장 때 본인 확인과 모순이라고 단정할 수 없다).
     */
    private static final Map<DeliveryMethod, List<RuleType>> CONFLICTS = new EnumMap<>(Map.of(
            DeliveryMethod.CAPTURE_QR, List.of(RuleType.CAPTURE_ENTRY_NOT_ALLOWED, RuleType.ON_SITE_PICKUP_REQUIRED),
            DeliveryMethod.ACCOUNT_TRANSFER, List.of(RuleType.IDENTITY_CHECK_REQUIRED, RuleType.ON_SITE_PICKUP_REQUIRED),
            DeliveryMethod.MOBILE_TICKET_TRANSFER, List.of(RuleType.ON_SITE_PICKUP_REQUIRED)));

    private static final Comparator<Finding> ORDER = Comparator
            .comparing((Finding f) -> f.severity().ordinal())
            .thenComparing(f -> f.type().ordinal());

    private final RuleEngineProperties properties;

    public RuleEngine(RuleEngineProperties properties) {
        this.properties = properties;
    }

    public List<Finding> evaluate(CheckRequest request, EventResponse event) {
        Evidence evidence = request.evidenceOrNone();
        List<Finding> findings = new ArrayList<>();
        priceFinding(request, evidence, event).ifPresent(findings::add);
        prepaymentFinding(request, evidence).ifPresent(findings::add);
        deliveryFinding(request, evidence, event).ifPresent(findings::add);
        transferProhibitedFinding(event).ifPresent(findings::add);
        quantityFinding(request, evidence).ifPresent(findings::add);
        findings.sort(ORDER);
        return List.copyOf(findings);
    }

    private Optional<Finding> priceFinding(CheckRequest request, Evidence evidence, EventResponse event) {
        Integer price = request.pricePerTicket();
        if (price == null) {
            return Optional.empty();
        }
        Optional<PriceTierResponse> matched = matchGrade(request.seat(), event.priceTiers());
        if (matched.isEmpty()) {
            return Optional.of(Finding.of(FindingType.PRICE_NOT_COMPARABLE, Severity.INFO,
                    FindingText.seatQuote(evidence, request.seat()), FindingText.priceTable(event.priceTiers()),
                    FindingText.notComparable(request.seat(), event.priceTiers()), null));
        }
        PriceTierResponse tier = matched.get();
        if (price <= tier.price()) {
            return Optional.empty();
        }
        double rawRatio = (double) price / tier.price(); // 판단은 원래 값으로, 반올림은 표시용으로만
        Severity severity = rawRatio >= properties.priceCheckRatio() ? Severity.CHECK : Severity.CAUTION;
        double shownRatio = Math.round(rawRatio * 100.0) / 100.0;
        return Optional.of(Finding.of(FindingType.PRICE_ABOVE_LIST, severity,
                FindingText.priceQuote(evidence, price),
                new OfficialReference(FindingText.listPrice(tier), tier.sourceUrl(), tier.checkedOn()),
                FindingText.aboveList(price, tier),
                details("ratio", shownRatio, "listPrice", tier.price(), "matchedGrade", tier.grade())));
    }

    private static Optional<Finding> prepaymentFinding(CheckRequest request, Evidence evidence) {
        if (!Boolean.TRUE.equals(request.prepaymentRequired())) {
            return Optional.empty();
        }
        return Optional.of(Finding.of(FindingType.PREPAYMENT_REQUESTED, Severity.CAUTION,
                FindingText.prepaymentQuote(evidence), null, FindingText.prepayment(), null));
    }

    private static Optional<Finding> deliveryFinding(CheckRequest request, Evidence evidence, EventResponse event) {
        DeliveryMethod method = request.deliveryMethod();
        if (method == null) {
            return Optional.empty();
        }
        for (RuleType conflicting : CONFLICTS.getOrDefault(method, List.of())) {
            Optional<RuleResponse> rule = firstRule(event, conflicting);
            if (rule.isPresent()) {
                return Optional.of(Finding.of(FindingType.DELIVERY_CONFLICTS_RULE, Severity.CHECK,
                        FindingText.deliveryQuote(evidence, method), official(rule.get()),
                        FindingText.deliveryConflict(method, rule.get()),
                        details("deliveryMethod", method.name(), "ruleType", conflicting.name())));
            }
        }
        return Optional.empty();
    }

    private static Optional<Finding> transferProhibitedFinding(EventResponse event) {
        return firstRule(event, RuleType.TRANSFER_PROHIBITED)
                .map(rule -> Finding.of(FindingType.TRANSFER_PROHIBITED, Severity.CHECK,
                        FindingText.TRANSFER_QUOTE, official(rule), FindingText.transferProhibited(), null));
    }

    private Optional<Finding> quantityFinding(CheckRequest request, Evidence evidence) {
        Integer quantity = request.quantity();
        if (quantity == null || quantity < properties.quantityHighThreshold()) {
            return Optional.empty();
        }
        return Optional.of(Finding.of(FindingType.QUANTITY_HIGH, Severity.INFO,
                FindingText.quantityQuote(evidence, quantity), null, FindingText.quantityHigh(quantity),
                details("quantity", quantity)));
    }

    /**
     * 좌석 글에 들어 있는 가장 긴 등급 이름을 고른다 ("s r 석 12열" → SR, not R). 공백·대소문자·전각은 무시.
     * 등급 앞뒤에 영문·숫자가 붙어 있으면 다른 단어의 일부로 보고 세지 않는다 ("Standing" 의 s 는 S석이 아니다).
     * 같은 길이의 등급이 둘 이상 들어 있거나 정가가 0원이면 추측하지 않고 "대조 불가"로 둔다.
     */
    static Optional<PriceTierResponse> matchGrade(String seat, List<PriceTierResponse> tiers) {
        String normalizedSeat = EventService.normalizeGrade(seat);
        if (normalizedSeat.isEmpty()) {
            return Optional.empty();
        }
        PriceTierResponse best = null;
        int bestLength = 0;
        boolean tie = false;
        for (PriceTierResponse tier : tiers) {
            String grade = EventService.normalizeGrade(tier.grade());
            if (grade.isEmpty() || tier.price() <= 0 || !containsAsWord(normalizedSeat, grade)) {
                continue;
            }
            if (grade.length() > bestLength) {
                best = tier;
                bestLength = grade.length();
                tie = false;
            } else if (grade.length() == bestLength) {
                tie = true;
            }
        }
        return tie ? Optional.empty() : Optional.ofNullable(best);
    }

    private static boolean containsAsWord(String normalizedSeat, String grade) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(grade) + "(?![a-z0-9])").matcher(normalizedSeat).find();
    }

    /** 키 순서가 고정된 details (Map.of 는 JVM 마다 순서가 달라 JSON 이 흔들린다). */
    private static Map<String, Object> details(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Optional<RuleResponse> firstRule(EventResponse event, RuleType type) {
        return event.rules().stream().filter(r -> r.type() == type).findFirst();
    }

    private static OfficialReference official(RuleResponse rule) {
        return new OfficialReference(rule.text(), rule.sourceUrl(), rule.checkedOn());
    }
}
