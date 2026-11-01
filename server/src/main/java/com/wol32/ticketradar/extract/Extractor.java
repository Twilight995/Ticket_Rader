package com.wol32.ticketradar.extract;

/**
 * AI 추출 부품의 자리 (ADR 5). 구현체는 ExtractorConfig 가 환경 변수로 고른다 (Gemini / Claude / none).
 * 실패는 예외로 알린다. 호출 쪽(ExtractService)이 시간 제한과 fallback 을 책임진다.
 */
public interface Extractor {

    Extraction extract(ExtractionInput input) throws Exception;
}
