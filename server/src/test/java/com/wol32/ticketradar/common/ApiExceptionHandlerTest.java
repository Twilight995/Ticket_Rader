package com.wol32.ticketradar.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wol32.ticketradar.event.EventController;
import com.wol32.ticketradar.event.EventService;

/** 9주차 점검 ⑤: 일부러 터뜨려도 사용자 문구만 나간다. */
@WebMvcTest(EventController.class)
class ApiExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EventService eventService;

    @Test
    void unexpected_error_is_500_with_fixed_korean_message_only() throws Exception {
        given(eventService.findEvent())
                .willThrow(new IllegalStateException("Table 'ticket_radar.events' doesn't exist at /srv/app"));

        mockMvc.perform(get("/api/event"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.message").value("잠시 문제가 생겼어요. 다시 시도해 주세요"))
                .andExpect(jsonPath("$.error.fields").isEmpty())
                .andExpect(content().string(not(containsString("events"))))
                .andExpect(content().string(not(containsString("/srv"))));
    }
}
