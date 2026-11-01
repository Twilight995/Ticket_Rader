package com.wol32.ticketradar.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 계약서 0절 `Event` 모양. 공개 조회·관리자 조회·관리자 저장이 같이 쓴다. */
public record EventResponse(
        Long id,
        String name,
        LocalDateTime startsAt,
        String venue,
        EventCategory category,
        String officialUrl,
        List<PriceTierResponse> priceTiers,
        List<RuleResponse> rules) {

    public record PriceTierResponse(Long id, String grade, int price, String sourceUrl, LocalDate checkedOn) {
        static PriceTierResponse from(PriceTier t) {
            return new PriceTierResponse(t.getId(), t.getGrade(), t.getPrice(), t.getSourceUrl(), t.getCheckedOn());
        }
    }

    public record RuleResponse(Long id, RuleType type, String text, String sourceUrl, LocalDate checkedOn) {
        static RuleResponse from(EventRule r) {
            return new RuleResponse(r.getId(), r.getType(), r.getText(), r.getSourceUrl(), r.getCheckedOn());
        }
    }

    static EventResponse from(Event e) {
        return new EventResponse(
                e.getId(), e.getName(), e.getStartsAt(), e.getVenue(), e.getCategory(), e.getOfficialUrl(),
                e.getPriceTiers().stream().map(PriceTierResponse::from).toList(),
                e.getRules().stream().map(RuleResponse::from).toList());
    }
}
