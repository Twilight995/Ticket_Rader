package com.wol32.ticketradar.extract;

import java.util.List;

/** AI 에 보내는 것. maskedText 는 연락처를 지운 뒤의 글(이미지만 있으면 null). seatGrades 는 공연의 등급 이름 후보. */
public record ExtractionInput(String maskedText, byte[] imageBytes, String imageMimeType, List<String> seatGrades) {

    boolean hasImage() {
        return imageBytes != null && imageBytes.length > 0 && imageMimeType != null;
    }
}
