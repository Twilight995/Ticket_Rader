package com.wol32.ticketradar.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** ADMIN_PASSWORD 가 비어 있으면 관리자 API는 열리지 않는다 (빈 비밀번호로 통과 금지). */
@SpringBootTest(properties = "ADMIN_PASSWORD=")
@AutoConfigureMockMvc
class AdminPasswordUnsetTest {

    @Autowired MockMvc mockMvc;

    @Test
    void admin_is_401_even_with_empty_password_header_when_not_configured() throws Exception {
        String emptyPw = "Basic " + Base64.getEncoder().encodeToString("admin:".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/admin/event").header("Authorization", emptyPw))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/event"))
                .andExpect(status().isUnauthorized());
    }
}
