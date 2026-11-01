package com.wol32.ticketradar.event;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wol32.ticketradar.common.ApiException;
import com.wol32.ticketradar.common.ApiExceptionHandler;

/** 공연 1건의 조회와 통째 교체. 규칙 확인(등급 중복)은 여기서 한다. */
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public Optional<EventResponse> findEvent() {
        return eventRepository.findFirstByOrderByIdAsc().map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<EventResponse> findEvent(long id) {
        return eventRepository.findById(id).map(EventResponse::from);
    }

    /** 있으면 모든 칸과 정가표·규정을 교체하고, 없으면 새로 만든다. 한 트랜잭션. */
    @Transactional
    public EventResponse replaceEvent(EventUpsertRequest request) {
        rejectDuplicateGrades(request.priceTiersOrEmpty());

        Event event = eventRepository.findFirstByOrderByIdAsc().orElseGet(Event::new);
        event.replaceWith(request.name().strip(), request.startsAt(), request.venue().strip(), request.category(),
                request.officialUrl().strip(), toTiers(request), toRules(request));
        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    private static void rejectDuplicateGrades(List<EventUpsertRequest.PriceTierRequest> tiers) {
        Set<String> seen = new HashSet<>();
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < tiers.size(); i++) {
            if (!seen.add(normalizeGrade(tiers.get(i).grade()))) {
                fields.put("priceTiers[" + i + "].grade", EventUpsertRequest.DUPLICATE_GRADE);
            }
        }
        if (!fields.isEmpty()) {
            throw ApiException.badRequest(ApiExceptionHandler.INVALID_MESSAGE, fields);
        }
    }

    /** "VIP", "vip", " V I P ", 전각 공백이 섞인 "VIP　" 를 같은 등급으로 본다 (모든 공백·대소문자 무시). 비교 API도 같은 규칙을 쓴다. */
    public static String normalizeGrade(String grade) {
        if (grade == null) {
            return "";
        }
        return Normalizer.normalize(grade, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Z}\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static List<PriceTier> toTiers(EventUpsertRequest request) {
        return request.priceTiersOrEmpty().stream()
                .map(t -> new PriceTier(t.grade().strip(), t.price(), t.sourceUrl().strip(), t.checkedOn()))
                .toList();
    }

    private static List<EventRule> toRules(EventUpsertRequest request) {
        return request.rulesOrEmpty().stream()
                .map(r -> new EventRule(r.type(), r.text().strip(), r.sourceUrl().strip(), r.checkedOn()))
                .toList();
    }
}
