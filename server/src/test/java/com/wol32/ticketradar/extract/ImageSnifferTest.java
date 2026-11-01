package com.wol32.ticketradar.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 절대 규칙 7: 이미지는 확장자가 아니라 파일 앞부분 바이트로 PNG/JPG 를 확인한다. */
class ImageSnifferTest {

    @Test
    void detects_png_and_jpeg_by_magic_bytes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

        assertThat(ImageSniffer.detect(png)).contains("image/png");
        assertThat(ImageSniffer.detect(jpeg)).contains("image/jpeg");
    }

    @Test
    void rejects_text_pretending_to_be_png_and_short_or_empty_input() {
        byte[] text = "hello this is not an image".getBytes(StandardCharsets.UTF_8);

        assertThat(ImageSniffer.detect(text)).isEmpty();
        assertThat(ImageSniffer.detect(new byte[] {(byte) 0x89, 'P'})).isEmpty();
        assertThat(ImageSniffer.detect(new byte[0])).isEmpty();
        assertThat(ImageSniffer.detect(null)).isEmpty();
    }
}
