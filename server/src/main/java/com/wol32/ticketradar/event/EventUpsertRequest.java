package com.wol32.ticketradar.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 계약서 5번 `PUT /api/admin/event` 의 보내는 것. 칸별 한국어 이유는 여기 한 곳에서 정한다. */
public record EventUpsertRequest(
        @NotBlank(message = REQUIRED) @Size(max = 100, message = "100자 이하로 입력해 주세요") String name,
        @NotNull(message = REQUIRED) LocalDateTime startsAt,
        @NotBlank(message = REQUIRED) @Size(max = 100, message = "100자 이하로 입력해 주세요") String venue,
        @NotNull(message = "공연 종류는 CONCERT 또는 SPORTS 중 하나예요") EventCategory category,
        @NotBlank(message = REQUIRED) @Size(max = 500, message = URL_TOO_LONG)
        @Pattern(regexp = HTTP_URL, message = URL_MESSAGE) String officialUrl,
        List<@NotNull(message = REQUIRED) @Valid PriceTierRequest> priceTiers,
        List<@NotNull(message = REQUIRED) @Valid RuleRequest> rules) {

    static final String REQUIRED = "값을 입력해 주세요";
    static final String HTTP_URL = "^https?://.+";
    static final String URL_MESSAGE = "http로 시작하는 주소를 넣어 주세요";
    static final String URL_TOO_LONG = "주소는 500자 이하여야 해요";
    static final String DUPLICATE_GRADE = "같은 등급 이름이 두 번 있어요";

    public record PriceTierRequest(
            @NotBlank(message = REQUIRED) @Size(max = 30, message = "30자 이하로 입력해 주세요") String grade,
            @NotNull(message = "숫자만 입력해 주세요") @Min(value = 0, message = "0 이상의 숫자를 넣어 주세요") Integer price,
            @NotBlank(message = REQUIRED) @Size(max = 500, message = URL_TOO_LONG)
            @Pattern(regexp = HTTP_URL, message = URL_MESSAGE) String sourceUrl,
            @NotNull(message = "날짜는 YYYY-MM-DD 형식으로 넣어 주세요") LocalDate checkedOn) {
    }

    public record RuleRequest(
            @NotNull(message = "규정 종류 5개 중 하나를 골라 주세요") RuleType type,
            @NotBlank(message = REQUIRED) @Size(max = 500, message = "500자 이하로 입력해 주세요") String text,
            @NotBlank(message = REQUIRED) @Size(max = 500, message = URL_TOO_LONG)
            @Pattern(regexp = HTTP_URL, message = URL_MESSAGE) String sourceUrl,
            @NotNull(message = "날짜는 YYYY-MM-DD 형식으로 넣어 주세요") LocalDate checkedOn) {
    }

    List<PriceTierRequest> priceTiersOrEmpty() {
        return priceTiers == null ? List.of() : priceTiers;
    }

    List<RuleRequest> rulesOrEmpty() {
        return rules == null ? List.of() : rules;
    }
}
