package com.wol32.ticketradar.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wol32.ticketradar.event.EventRepository;
import com.wol32.ticketradar.event.EventSeeder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 6단계 검증표(docs/validation.md)의 "기대 추출값 → 기대 확인사항"을 실제 요청으로 확인한다.
 * 데이터는 검증표와 같은 파일 docs/validation/cases.json 하나만 쓴다 (Maven 은 server 폴더에서 돌기 때문에 ../docs).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationCasesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path CASES = Path.of("..", "docs", "validation", "cases.json");

    @Autowired MockMvc mockMvc;
    @Autowired EventRepository eventRepository;
    @Autowired EventSeeder eventSeeder;

    private long eventId;

    @BeforeEach
    void seed() throws Exception {
        eventRepository.deleteAll();
        eventSeeder.run(null);
        eventId = eventRepository.findFirstByOrderByIdAsc().orElseThrow().getId();
    }

    static Stream<JsonNode> cases() {
        JsonNode cases = JSON.readTree(CASES.toFile()).get("cases");
        assertThat(cases.size()).as("가상 판매글 10건").isEqualTo(10);
        return cases.valueStream();
    }

    @ParameterizedTest(name = "검증 글 {index}")
    @MethodSource("cases")
    void expected_extraction_yields_expected_findings(JsonNode testCase) throws Exception {
        ObjectNode request = ((ObjectNode) testCase.get("expected").deepCopy()).put("eventId", eventId);

        String body = mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        List<String> actual = new ArrayList<>();
        JSON.readTree(body).at("/data/findings").forEach(f -> actual.add(f.get("type").asString() + ":" + f.get("severity").asString()));
        List<String> expected = new ArrayList<>();
        testCase.get("expectedFindings").forEach(f -> expected.add(f.asString()));

        assertThat(actual).as("글 %s", testCase.get("id")).containsExactlyElementsOf(expected);
    }
}
