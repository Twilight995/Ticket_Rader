package com.wol32.ticketradar.extract;

import java.util.Optional;

/** 파일 앞부분 바이트로만 PNG / JPG 를 판별한다 (확장자·Content-Type 은 믿지 않는다, 절대 규칙 7). */
final class ImageSniffer {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private ImageSniffer() {
    }

    static Optional<String> detect(byte[] bytes) {
        if (startsWith(bytes, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(bytes, JPEG)) {
            return Optional.of("image/jpeg");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes == null || bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
