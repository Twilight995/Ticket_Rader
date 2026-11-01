package com.wol32.ticketradar.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wol32.ticketradar.common.ApiResponse;

/** 계약서 1번: 공연 조회 (누구나). ⓪ 홈과 ① 입력이 쓴다. */
@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/api/event")
    public ApiResponse<EventResponse> getEvent() {
        return ApiResponse.ok(eventService.findEvent().orElse(null));
    }
}
