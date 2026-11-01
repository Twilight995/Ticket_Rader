package com.wol32.ticketradar.extract;

/** 키가 없거나 공급자가 none 일 때. 항상 실패 → 서비스가 fallback 으로 돌려준다 (AI 없이도 끝까지 동작). */
final class NoneExtractor implements Extractor {

    @Override
    public Extraction extract(ExtractionInput input) {
        throw new IllegalStateException("AI provider is not configured");
    }
}
