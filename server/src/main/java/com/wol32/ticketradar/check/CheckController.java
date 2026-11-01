package com.wol32.ticketradar.check;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wol32.ticketradar.common.ApiResponse;

/** 계약서 3번: 비교. 받아서 넘기고 봉투에 담을 뿐, 판단은 CheckService / RuleEngine 이 한다. */
@RestController
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    @PostMapping("/api/check")
    public ApiResponse<CheckResponse> check(@Valid @RequestBody CheckRequest request) {
        return ApiResponse.ok(checkService.check(request));
    }
}
