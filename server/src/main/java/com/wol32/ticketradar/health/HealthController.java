package com.wol32.ticketradar.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wol32.ticketradar.common.ApiResponse;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "up"));
    }
}
