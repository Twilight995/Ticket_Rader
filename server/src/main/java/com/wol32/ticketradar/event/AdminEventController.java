package com.wol32.ticketradar.event;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wol32.ticketradar.common.ApiResponse;

/** 계약서 4·5번. 비밀번호 확인은 AdminAuthFilter 가 /api/admin/** 전체에 대해 먼저 한다. */
@RestController
@RequestMapping("/api/admin/event")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ApiResponse<EventResponse> getEvent() {
        return ApiResponse.ok(eventService.findEvent().orElse(null));
    }

    @PutMapping
    public ApiResponse<EventResponse> replaceEvent(@Valid @RequestBody EventUpsertRequest request) {
        return ApiResponse.ok(eventService.replaceEvent(request));
    }
}
