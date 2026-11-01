package com.wol32.ticketradar.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wol32.ticketradar.check.CheckRequest.Evidence;
import com.wol32.ticketradar.check.CheckResponse.Finding;
import com.wol32.ticketradar.event.EventCategory;
import com.wol32.ticketradar.event.EventResponse;
import com.wol32.ticketradar.event.EventResponse.PriceTierResponse;
import com.wol32.ticketradar.event.EventResponse.RuleResponse;
import com.wol32.ticketradar.event.RuleType;

/**
 * 3단계 핵심: 규칙 엔진은 순수 함수다. Spring 없이 돌고, 같은 입력이면 같은 결과, 기준값은 설정에서 온다.
 * 수용 기준 2(정상 양도 글 2건 → 0건)와 시연 글(정확히 3건)이 여기 있다.
 */
class RuleEngineTest {

    private static final LocalDate CHECKED = LocalDate.of(2026, 10, 28);
    private static final String PRICE_URL = "https://example.com/notice#price";
    private static final String RULES_URL = "https://example.com/notice#rules";

    /** 가상 공연: VIP 165,000 / R 143,000 / S 121,000, 규정: 현장 수령 필수·캡처 입장 불가·본인 확인 필수 */
    private static final EventResponse EVENT = event(List.of(
            new RuleResponse(1L, RuleType.ON_SITE_PICKUP_REQUIRED, "현장에서 본인이 수령합니다.", RULES_URL, CHECKED),
            new RuleResponse(2L, RuleType.CAPTURE_ENTRY_NOT_ALLOWED, "캡처 화면으로는 입장할 수 없습니다.", RULES_URL, CHECKED),
            new RuleResponse(3L, RuleType.IDENTITY_CHECK_REQUIRED, "입장 시 본인 확인을 합니다.", RULES_URL, CHECKED)));

    private static final RuleEngineProperties DEFAULTS = new RuleEngineProperties(1.5, 4);

    private final RuleEngine engine = new RuleEngine(DEFAULTS);

    private static EventResponse event(List<RuleResponse> rules) {
        return new EventResponse(1L, "2026 테스트 콘서트 (가상)", LocalDateTime.of(2026, 12, 20, 18, 0),
                "테스트 아레나 (가상)", EventCategory.CONCERT, "https://example.com/notice",
                List.of(new PriceTierResponse(1L, "VIP", 165000, PRICE_URL, CHECKED),
                        new PriceTierResponse(2L, "R", 143000, PRICE_URL, CHECKED),
                        new PriceTierResponse(3L, "S", 121000, PRICE_URL, CHECKED)),
                rules);
    }

    private static CheckRequest request(Integer price, String seat, Integer quantity, DeliveryMethod delivery,
            Boolean prepayment, Evidence evidence) {
        return new CheckRequest(1L, price, seat, quantity, delivery, prepayment, evidence);
    }

    private static List<FindingType> typesOf(List<Finding> findings) {
        return findings.stream().map(Finding::type).toList();
    }

    // --- 수용 기준 2: 정가 이하 1장 정상 양도 글 2건 → 0건 ---

    @Test
    void normal_listing_at_list_price_on_site_pickup_has_no_findings() {
        CheckRequest normal = request(165000, "VIP석", 1, DeliveryMethod.ON_SITE_PICKUP, false, null);

        assertThat(engine.evaluate(normal, EVENT)).isEmpty();
    }

    @Test
    void normal_listing_below_list_price_without_delivery_info_has_no_findings() {
        CheckRequest normal = request(120000, "S석 2층", 1, null, null, null);

        assertThat(engine.evaluate(normal, EVENT)).isEmpty();
    }

    // --- 시연 글: 정가 1.5배 + 선입금 + QR 캡처 → 정확히 3건, 심각도 순 ---

    @Test
    void demo_listing_yields_exactly_three_findings_sorted_by_severity() {
        Evidence evidence = new Evidence("장당 25만", "VIP석 A구역", "2장", "QR 캡처로 보내드려요", "선입금 확인 후");
        CheckRequest demo = request(250000, "VIP석 A구역", 2, DeliveryMethod.CAPTURE_QR, true, evidence);

        List<Finding> findings = engine.evaluate(demo, EVENT);

        assertThat(typesOf(findings)).containsExactly(
                FindingType.PRICE_ABOVE_LIST, FindingType.DELIVERY_CONFLICTS_RULE, FindingType.PREPAYMENT_REQUESTED);
        assertThat(findings).extracting(Finding::severity)
                .containsExactly(Severity.CHECK, Severity.CHECK, Severity.CAUTION);

        Finding price = findings.get(0);
        assertThat(price.title()).isEqualTo("정가보다 높음");
        assertThat(price.severityLabel()).isEqualTo("확인 필요");
        assertThat(price.listingQuote()).isEqualTo("장당 25만");
        assertThat(price.official().text()).contains("VIP").contains("165,000");
        assertThat(price.official().sourceUrl()).isEqualTo(PRICE_URL);
        assertThat(price.official().checkedOn()).isEqualTo(CHECKED);
        assertThat(price.details()).containsEntry("listPrice", 165000).containsEntry("matchedGrade", "VIP");
        assertThat(((Number) price.details().get("ratio")).doubleValue()).isBetween(1.51, 1.52);

        Finding delivery = findings.get(1);
        assertThat(delivery.listingQuote()).isEqualTo("QR 캡처로 보내드려요");
        assertThat(delivery.official().text()).isEqualTo("캡처 화면으로는 입장할 수 없습니다.");
        assertThat(delivery.details()).containsEntry("ruleType", "CAPTURE_ENTRY_NOT_ALLOWED");

        Finding prepayment = findings.get(2);
        assertThat(prepayment.listingQuote()).isEqualTo("선입금 확인 후");
        assertThat(prepayment.official()).isNull();
        assertThat(prepayment.explanation()).isNotBlank();
    }

    // --- 기준값을 바꾸면 결과가 바뀐다 (숫자는 코드가 아니라 설정에) ---

    @Test
    void raising_price_ratio_threshold_lowers_severity_to_caution() {
        RuleEngine lenient = new RuleEngine(new RuleEngineProperties(2.0, 4));
        CheckRequest demo = request(250000, "VIP", 2, DeliveryMethod.ON_SITE_PICKUP, false, null);

        Finding price = lenient.evaluate(demo, EVENT).get(0);

        assertThat(price.type()).isEqualTo(FindingType.PRICE_ABOVE_LIST);
        assertThat(price.severity()).isEqualTo(Severity.CAUTION);
        assertThat(price.severityLabel()).isEqualTo("주의");
    }

    @Test
    void lowering_quantity_threshold_adds_quantity_finding() {
        RuleEngine strict = new RuleEngine(new RuleEngineProperties(1.5, 2));
        CheckRequest twoTickets = request(165000, "VIP", 2, DeliveryMethod.ON_SITE_PICKUP, false, null);

        assertThat(typesOf(engine.evaluate(twoTickets, EVENT))).isEmpty();
        List<Finding> findings = strict.evaluate(twoTickets, EVENT);
        assertThat(typesOf(findings)).containsExactly(FindingType.QUANTITY_HIGH);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(findings.get(0).listingQuote()).isEqualTo("2장 (직접 입력)");
    }

    // --- 같은 입력 두 번 → 완전히 같은 결과 ---

    @Test
    void same_input_gives_identical_result() {
        Evidence evidence = new Evidence("장당 25만", null, "2장", "QR", "선입금");
        CheckRequest demo = request(250000, "VIP석", 2, DeliveryMethod.CAPTURE_QR, true, evidence);

        assertThat(engine.evaluate(demo, EVENT)).isEqualTo(engine.evaluate(demo, EVENT));
    }

    // --- 정가 대조 불가: 오류가 아니라 "정보" 확인사항 ---

    @Test
    void unknown_seat_grade_is_not_comparable_instead_of_above_list() {
        CheckRequest unknownGrade = request(300000, "스탠딩 A", 1, DeliveryMethod.ON_SITE_PICKUP, false, null);

        List<Finding> findings = engine.evaluate(unknownGrade, EVENT);

        assertThat(typesOf(findings)).containsExactly(FindingType.PRICE_NOT_COMPARABLE);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(findings.get(0).official().text()).contains("VIP").contains("R").contains("S");
        assertThat(findings.get(0).official().sourceUrl()).isEqualTo(PRICE_URL);
    }

    @Test
    void missing_seat_with_price_is_not_comparable() {
        CheckRequest noSeat = request(300000, null, 1, null, null, null);

        assertThat(typesOf(engine.evaluate(noSeat, EVENT))).containsExactly(FindingType.PRICE_NOT_COMPARABLE);
    }

    @Test
    void grade_matching_ignores_case_spaces_and_suffixes_and_prefers_longest_grade() {
        EventResponse withSr = new EventResponse(1L, EVENT.name(), EVENT.startsAt(), EVENT.venue(), EVENT.category(),
                EVENT.officialUrl(),
                List.of(new PriceTierResponse(1L, "R", 143000, PRICE_URL, CHECKED),
                        new PriceTierResponse(2L, "SR", 180000, PRICE_URL, CHECKED)),
                List.of());

        List<Finding> findings = engine.evaluate(request(200000, " s r 석 12열", 1, null, null, null), withSr);

        assertThat(findings.get(0).type()).isEqualTo(FindingType.PRICE_ABOVE_LIST);
        assertThat(findings.get(0).details()).containsEntry("matchedGrade", "SR").containsEntry("listPrice", 180000);
    }

    // --- 전달 방식 충돌: 종류당 카드 1장, 가장 직접적인 규정을 고른다 ---

    @Test
    void account_transfer_conflicts_with_identity_check_rule_first() {
        CheckRequest account = request(165000, "VIP", 1, DeliveryMethod.ACCOUNT_TRANSFER, false, null);

        List<Finding> findings = engine.evaluate(account, EVENT);

        assertThat(typesOf(findings)).containsExactly(FindingType.DELIVERY_CONFLICTS_RULE);
        assertThat(findings.get(0).details()).containsEntry("ruleType", "IDENTITY_CHECK_REQUIRED");
        assertThat(findings.get(0).listingQuote()).contains("계정 양도").contains("직접 입력");
    }

    @Test
    void delivery_method_without_conflicting_rule_is_silent() {
        EventResponse noRules = event(List.of());

        assertThat(engine.evaluate(request(165000, "VIP", 1, DeliveryMethod.CAPTURE_QR, false, null), noRules)).isEmpty();
        assertThat(engine.evaluate(request(165000, "VIP", 1, DeliveryMethod.OTHER, false, null), EVENT)).isEmpty();
    }

    // --- 양도 금지 공연: 입력과 무관하게 항상 ---

    @Test
    void transfer_prohibited_event_always_has_that_finding() {
        EventResponse prohibited = event(List.of(
                new RuleResponse(9L, RuleType.TRANSFER_PROHIBITED, "본 공연 티켓은 타인에게 양도할 수 없습니다.", RULES_URL, CHECKED)));

        List<Finding> findings = engine.evaluate(request(100000, "VIP", 1, DeliveryMethod.ON_SITE_PICKUP, false, null), prohibited);

        assertThat(typesOf(findings)).containsExactly(FindingType.TRANSFER_PROHIBITED);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.CHECK);
        assertThat(findings.get(0).official().text()).isEqualTo("본 공연 티켓은 타인에게 양도할 수 없습니다.");
    }

    // --- 근거 구절이 없으면 "(직접 입력)" 문장을 서버가 만든다 ---

    @Test
    void listing_quote_falls_back_to_entered_value_when_no_evidence() {
        CheckRequest typed = request(250000, "VIP", 1, DeliveryMethod.ON_SITE_PICKUP, true, new Evidence(null, null, null, null, null));

        List<Finding> findings = engine.evaluate(typed, EVENT);

        assertThat(findings).extracting(Finding::listingQuote)
                .containsExactly("1장당 250,000원 (직접 입력)", "선입금 요구 (직접 입력)");
    }

    // --- 금지 단어: 어떤 종류의 카드 문장에도 없다 ---

    @Test
    void no_forbidden_words_in_any_generated_text() {
        EventResponse everything = event(List.of(
                new RuleResponse(1L, RuleType.CAPTURE_ENTRY_NOT_ALLOWED, "캡처 입장 불가", RULES_URL, CHECKED),
                new RuleResponse(2L, RuleType.TRANSFER_PROHIBITED, "양도 금지", RULES_URL, CHECKED)));
        List<Finding> all = new java.util.ArrayList<>();
        all.addAll(engine.evaluate(request(400000, "VIP", 10, DeliveryMethod.CAPTURE_QR, true, null), everything));
        all.addAll(engine.evaluate(request(400000, "모름", 1, null, null, null), everything));
        assertThat(typesOf(all)).containsAll(List.of(FindingType.values()));

        for (Finding f : all) {
            String text = f.title() + " " + f.explanation() + " " + f.listingQuote() + " " + f.severityLabel();
            assertThat(text).doesNotContainPattern("암표|불법|사기|상습|범죄|안전합니다");
        }
    }
}
