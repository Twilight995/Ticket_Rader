package com.wol32.ticketradar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 절대 규칙 4: 코드·주석·템플릿·seed 어디에도 금지 단어가 없다 (공식 기관 이름 안에서만 예외).
 * "안전합니다"도 결과 문구로 쓰지 않는다.
 */
class ForbiddenWordsTest {

    private static final Pattern FORBIDDEN = Pattern.compile("암표|불법|사기|상습|범죄|안전합니다");

    @Test
    void server_sources_and_resources_contain_no_forbidden_words() throws IOException {
        Path mainRoot = Path.of("src", "main");
        try (Stream<Path> files = Files.walk(mainRoot)) {
            List<String> offenders = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(java|properties|json)$"))
                    .filter(ForbiddenWordsTest::containsForbiddenWord)
                    .map(Path::toString)
                    .toList();
            assertThat(offenders).as("금지 단어가 들어 있는 파일").isEmpty();
        }
    }

    private static boolean containsForbiddenWord(Path file) {
        try {
            return FORBIDDEN.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
