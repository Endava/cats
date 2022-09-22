package com.endava.cats.args;

import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@QuarkusTest
class IgnoreArgumentsTest {

    private IgnoreArguments ignoreArguments;

    @BeforeEach
    void setup() {
        ignoreArguments = new IgnoreArguments();
    }

    @Test
    void shouldReturnIgnoreHttpCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("200", "4XX"));
        List<String> ignoredCodes = ignoreArguments.getIgnoreResponseCodes();

        Assertions.assertThat(ignoredCodes).containsOnly("200", "4XX");
    }

    @Test
    void shouldReturnSkippedFields() {
        ReflectionTestUtils.setField(ignoreArguments, "skipFields", List.of("field1", "field2"));

        List<String> skipFields = ignoreArguments.getSkipFields();
        Assertions.assertThat(skipFields).containsOnly("field1", "field2");
    }

    @Test
    void shouldReturnEmptySkipFields() {
        Assertions.assertThat(ignoreArguments.getSkipFields()).isEmpty();
    }

    @Test
    void shouldMatchIgnoredResponseCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2XX", "400"));
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("202")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("400")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("404")).isFalse();
    }

    @Test
    void shouldMatchIgnoredResponseCodesWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
    }

    @Test
    void shouldNotMatchIgnoredResponseCodesWhenBlank() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2XX", "400"));
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("")).isFalse();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode(null)).isFalse();
    }

    @Test
    void shouldNotMatchIgnoredResponseCodesWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("4xx"));
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", false);
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isFalse();
    }

    @Test
    void shouldNotIgnoreLines() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseLines", List.of(200L));
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponseLines(200)).isFalse();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isFalse();
        Assertions.assertThat(ignoreArguments.isIgnoredResponse(catsResponse)).isTrue();
    }

    @Test
    void shouldIgnoreLines() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseLines", null);
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponseLines(200)).isTrue();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isTrue();
    }

    @Test
    void shouldNotIgnoreWords() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseWords", List.of(200L));
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponseWords(200)).isFalse();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isFalse();
    }

    @Test
    void shouldIgnoreWords() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseWords", null);
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponseWords(200)).isTrue();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isTrue();
    }

    @Test
    void shouldNotIgnoreSizes() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseSizes", List.of(200L));
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponseLength(200)).isFalse();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isFalse();
    }

    @Test
    void shouldIgnoreSizes() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseSizes", null);
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponseLength(200)).isTrue();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isTrue();
    }

    @Test
    void shouldNotIgnoreRegex() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{\"err\":\"error 333\"}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseRegex", ".*error.*");
        Assertions.assertThat(ignoreArguments.isNotIgnoredRegex("error 333")).isFalse();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isFalse();
    }

    @Test
    void shouldIgnoreRegex() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("{\"error\":\"value\"}")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseRegex", null);
        Assertions.assertThat(ignoreArguments.isNotIgnoredRegex("error")).isTrue();
        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"1,0,0,0,false", "0,1,0,0,false", "0,0,1,0,false", "0,0,0,1,false", "0,0,0,0,true"})
    void shouldReturnIgnoredArgumentSupplied(long words, long sizes, long lines, long code, boolean blackbox) {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseSizes", sizes != 0 ? List.of(sizes) : null);
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseWords", words != 0 ? List.of(words) : null);
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseLines", lines != 0 ? List.of(lines) : null);
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", code != 0 ? List.of(String.valueOf(code)) : null);
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", blackbox);

        Assertions.assertThat(ignoreArguments.isAnyIgnoredArgumentSupplied()).isTrue();
    }

    @Test
    void shouldNotIgnoreCatsResponse() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200).body("test")
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();

        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isTrue();
    }

    @Test
    void shouldIgnoreCatsResponse() {
        CatsResponse catsResponse = CatsResponse.builder().responseCode(200)
                .numberOfLinesInResponse(200).numberOfWordsInResponse(200).contentLengthInBytes(200).build();
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseSizes", List.of(200L));
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseWords", List.of(200L));
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseLines", List.of(200L));
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("200"));

        Assertions.assertThat(ignoreArguments.isNotIgnoredResponse(catsResponse)).isFalse();
    }
}
