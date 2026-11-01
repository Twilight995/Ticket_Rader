package com.wol32.ticketradar.extract;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wol32.ticketradar.common.ApiResponse;

/** 계약서 2번: 판매글 추출. 폼(multipart) 으로 받아 서비스에 넘길 뿐, 확인·판단은 ExtractService 가 한다. */
@RestController
public class ExtractController {

    private final ExtractService extractService;

    public ExtractController(ExtractService extractService) {
        this.extractService = extractService;
    }

    @PostMapping(value = "/api/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExtractResponse> extract(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile image,
            HttpServletRequest request) {
        return ApiResponse.ok(extractService.extract(eventId, text, image, request.getRemoteAddr()));
    }
}
