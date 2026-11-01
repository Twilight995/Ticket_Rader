package com.wol32.ticketradar.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2단계 수용 테스트. 계약서 1·4·5번과 9주차 점검 ①③을 실제 요청으로 확인한다. (H2, 관리자 비밀번호는 테스트 값)
 */
@SpringBootTest(properties = "ADMIN_PASSWORD=test-secret")
@AutoConfigureMockMvc
class EventApiTest {

    private static final String ADMIN = basic("admin:test-secret");
    private static final String WRONG = basic("admin:nope");

    private static final String VALID_EVENT = """
            {
              "name": "2026 테스트 콘서트 (가상)",
              "startsAt": "2026-12-20T18:00:00",
              "venue": "테스트 아레나 (가상)",
              "category": "CONCERT",
              "officialUrl": "https://example.com/notice",
              "priceTiers": [
                { "grade": "VIP", "price": 165000, "sourceUrl": "https://example.com/notice#price", "checkedOn": "2026-10-28" },
                { "grade": "R",   "price": 143000, "sourceUrl": "https://example.com/notice#price", "checkedOn": "2026-10-28" }
              ],
              "rules": [
                { "type": "ON_SITE_PICKUP_REQUIRED", "text": "현장 수령", "sourceUrl": "https://example.com/notice#rules", "checkedOn": "2026-10-28" }
              ]
            }
            """;

    @Autowired MockMvc mockMvc;
    @Autowired EventRepository eventRepository;
    @Autowired EventSeeder eventSeeder;

    private static String basic(String userAndPassword) {
        return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void resetToSeed() throws Exception {
        eventRepository.deleteAll();
        eventSeeder.run(null);
    }

    // --- 공연 조회 (계약서 1) ---

    @Test
    void public_event_returns_seeded_fictional_event_with_tiers_and_rules() throws Exception {
        mockMvc.perform(get("/api/event"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.name").value(containsString("가상")))
                .andExpect(jsonPath("$.data.officialUrl").value(startsWith("http")))
                .andExpect(jsonPath("$.data.priceTiers[0].grade").value("VIP"))
                .andExpect(jsonPath("$.data.priceTiers[0].price").value(165000))
                .andExpect(jsonPath("$.data.priceTiers[0].checkedOn").value("2026-10-28"))
                .andExpect(jsonPath("$.data.rules[*].type").isArray())
                .andExpect(jsonPath("$.data.rules[0].sourceUrl").value(startsWith("http")));
    }

    @Test
    void public_event_returns_null_data_when_no_event() throws Exception {
        eventRepository.deleteAll();

        mockMvc.perform(get("/api/event"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void seeding_twice_does_not_duplicate() throws Exception {
        eventSeeder.run(null);
        eventSeeder.run(null);

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    // --- 관리자 인증 (계약서 4, 점검 ①) ---

    @Test
    void admin_without_password_is_401_with_www_authenticate() throws Exception {
        mockMvc.perform(get("/api/admin/event"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.message").value("관리자 비밀번호가 필요해요"));
    }

    @Test
    void admin_with_wrong_password_is_401() throws Exception {
        mockMvc.perform(get("/api/admin/event").header("Authorization", WRONG))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/event").header("Authorization", WRONG)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_path_variants_cannot_bypass_the_password() throws Exception {
        // 경로 매개변수(;)·인코딩·이중 슬래시로 우회하면 안 된다 (코드 리뷰에서 발견)
        mockMvc.perform(get("/api/admin;x=1/event")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin;x=1/event").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/%61dmin/event")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("//api/admin/event")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api//admin/event")).andExpect(status().is4xxClientError());
    }

    @Test
    void malformed_basic_headers_are_401_and_scheme_is_case_insensitive() throws Exception {
        mockMvc.perform(get("/api/admin/event").header("Authorization", "Basic not-base64!"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/event").header("Authorization", basic("admintest-secret")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/event").header("Authorization", basic("root:test-secret")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/event").header("Authorization", "basic " + ADMIN.substring(6)))
                .andExpect(status().isOk());
    }

    @Test
    void framework_errors_keep_their_status_and_the_envelope() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.message").value("요청한 주소를 찾을 수 없어요"));
        mockMvc.perform(post("/api/event"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.message").value("허용되지 않는 요청 방식이에요"));
        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.message").value("지원하지 않는 요청 형식이에요"));
    }

    @Test
    void null_list_element_and_overlong_url_are_400_naming_the_field() throws Exception {
        String nullTier = VALID_EVENT.replace("\"priceTiers\": [", "\"priceTiers\": [ null,");
        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(nullTier))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['priceTiers[0]']").isString());

        String longUrl = VALID_EVENT.replace("\"officialUrl\": \"https://example.com/notice\"",
                "\"officialUrl\": \"https://example.com/" + "a".repeat(500) + "\"");
        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(longUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.officialUrl").value("주소는 500자 이하여야 해요"));
    }

    @Test
    void admin_get_returns_same_event_as_public() throws Exception {
        mockMvc.perform(get("/api/admin/event").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceTiers[0].grade").value("VIP"))
                .andExpect(jsonPath("$.data.rules").isArray());
    }

    // --- 관리자 저장 (계약서 5, 점검 ③) ---

    @Test
    void put_replaces_event_and_public_get_shows_it() throws Exception {
        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.priceTiers.length()").value(2))
                .andExpect(jsonPath("$.data.priceTiers[1].id").isNumber())
                .andExpect(jsonPath("$.data.rules.length()").value(1));

        mockMvc.perform(get("/api/event"))
                .andExpect(jsonPath("$.data.name").value("2026 테스트 콘서트 (가상)"))
                .andExpect(jsonPath("$.data.priceTiers.length()").value(2))
                .andExpect(jsonPath("$.data.priceTiers[1].grade").value("R"))
                .andExpect(jsonPath("$.data.rules[0].type").value("ON_SITE_PICKUP_REQUIRED"));

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void put_with_bad_url_negative_price_duplicate_grade_is_400_with_fields() throws Exception {
        String bad = """
                {
                  "name": "", "startsAt": "2026-12-20T18:00:00", "venue": "어딘가", "category": "CONCERT",
                  "officialUrl": "ftp://example.com",
                  "priceTiers": [
                    { "grade": "VIP", "price": -1,    "sourceUrl": "https://example.com", "checkedOn": "2026-10-28" },
                    { "grade": "vip", "price": 10000, "sourceUrl": "https://example.com", "checkedOn": "2026-10-28" }
                  ],
                  "rules": []
                }
                """;
        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.fields.name").isString())
                .andExpect(jsonPath("$.error.fields.officialUrl").value("http로 시작하는 주소를 넣어 주세요"))
                .andExpect(jsonPath("$.error.fields['priceTiers[0].price']").isString());

        // 거절된 요청은 아무것도 바꾸지 않는다
        mockMvc.perform(get("/api/event"))
                .andExpect(jsonPath("$.data.name").value(containsString("가상")));
    }

    @Test
    void put_with_duplicate_grade_ignoring_case_and_spaces_is_400_naming_the_row() throws Exception {
        String duplicate = VALID_EVENT.replace("\"grade\": \"R\"", "\"grade\": \"v i p\u3000\"");
        assertThat(duplicate).contains("v i p");

        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['priceTiers[1].grade']").value("같은 등급 이름이 두 번 있어요"));
    }

    @Test
    void put_with_unknown_rule_type_is_400_naming_the_field() throws Exception {
        String badType = VALID_EVENT.replace("ON_SITE_PICKUP_REQUIRED", "SOMETHING_ELSE");

        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(badType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['rules[0].type']").value("규정 종류 5개 중 하나를 골라 주세요"));
    }

    @Test
    void put_with_bad_date_is_400_naming_the_field() throws Exception {
        String badDate = VALID_EVENT.replace("\"price\": 143000, \"sourceUrl\": \"https://example.com/notice#price\", \"checkedOn\": \"2026-10-28\"",
                "\"price\": 143000, \"sourceUrl\": \"https://example.com/notice#price\", \"checkedOn\": \"28/10/2026\"");
        assertThat(badDate).contains("28/10/2026");

        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(badDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['priceTiers[1].checkedOn']").value("날짜는 YYYY-MM-DD 형식으로 넣어 주세요"));
    }

    @Test
    void put_with_broken_json_is_400_without_internal_details() throws Exception {
        mockMvc.perform(put("/api/admin/event").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않아요"));
    }
}
