package com.endava.cats.args;

import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

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
    void shouldMatchIgnoredResponseCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2XX", "400"));
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("202")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("400")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("404")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"200,true", "501,true", "500,false", "400,true", "503,false"})
    void shouldMatchIgnoredResponseCodesWhenBlackbox(String responseCode, boolean expected) {
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode(responseCode)).isEqualTo(expected);
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

    @Test
    void shouldIgnoreResponseLinesWhenFiltering() {
        ignoreArguments.setFilterResponseLines(List.of(100L));
        Assertions.assertThat(ignoreArguments.getIgnoreResponseLines()).containsOnly(100L);
        Assertions.assertThat(ignoreArguments.isSkipReportingForIgnoredCodes()).isTrue();
    }

    @Test
    void shouldIgnoreResponseCodesCWhenFiltering() {
        ignoreArguments.setFilterResponseCodes(List.of("200", "400"));
        Assertions.assertThat(ignoreArguments.getIgnoreResponseCodes()).containsOnly("200", "400");
        Assertions.assertThat(ignoreArguments.isSkipReportingForIgnoredCodes()).isTrue();
    }

    @Test
    void shouldIgnoreResponseWordsWhenFiltering() {
        ignoreArguments.setFilterResponseWords(List.of(200L));
        Assertions.assertThat(ignoreArguments.getIgnoreResponseWords()).containsOnly(200L);
        Assertions.assertThat(ignoreArguments.isSkipReportingForIgnoredCodes()).isTrue();
    }

    @Test
    void shouldIgnoreResponseSizesWhenFiltering() {
        ignoreArguments.setFilterResponseSize(List.of(500L));
        Assertions.assertThat(ignoreArguments.getIgnoreResponseSizes()).containsOnly(500L);
        Assertions.assertThat(ignoreArguments.isSkipReportingForIgnoredCodes()).isTrue();
    }


    @Test
    void shouldIgnoreResponseRegexWhenFiltering() {
        ignoreArguments.setFilterResponseRegex("regex");
        Assertions.assertThat(ignoreArguments.getIgnoreResponseRegex()).isEqualTo("regex");
        Assertions.assertThat(ignoreArguments.isSkipReportingForIgnoredCodes()).isTrue();
    }


    static Stream<IgnoreArguments> provideIgnoreArguments() {
        IgnoreArguments case1 = new IgnoreArguments();
        case1.setFilterResponseCodes(List.of("200"));

        IgnoreArguments case2 = new IgnoreArguments();
        case2.setFilterResponseSize(List.of(100L));

        IgnoreArguments case3 = new IgnoreArguments();
        case3.setFilterResponseWords(List.of(50L));

        IgnoreArguments case4 = new IgnoreArguments();
        case4.setFilterResponseLines(List.of(10L));

        IgnoreArguments case5 = new IgnoreArguments();
        case5.setFilterResponseRegex(".*error.*");

        IgnoreArguments case6 = new IgnoreArguments();
        case6.setFilterResponseCodes(List.of("200"));
        case6.setFilterResponseSize(List.of(100L));
        case6.setFilterResponseWords(List.of(50L));
        case6.setFilterResponseLines(List.of(10L));
        case6.setFilterResponseRegex(".*error.*");

        IgnoreArguments case7 = new IgnoreArguments();
        ReflectionTestUtils.setField(case7, "blackbox", true);

        IgnoreArguments case8 = new IgnoreArguments();

        return Stream.of(case1, case2, case3, case4, case5, case6, case7, case8);
    }

    @ParameterizedTest
    @MethodSource("provideIgnoreArguments")
    void testIsAnyIgnoreArgumentSupplied(IgnoreArguments ignoreArguments) {
        boolean expected = !ignoreArguments.getIgnoreResponseCodes().isEmpty() ||
                ignoreArguments.getIgnoreResponseSizes() != null ||
                ignoreArguments.getIgnoreResponseWords() != null ||
                ignoreArguments.getIgnoreResponseLines() != null ||
                ignoreArguments.getIgnoreResponseRegex() != null ||
                ignoreArguments.isBlackbox();

        Assertions.assertThat(ignoreArguments.isAnyIgnoreArgumentSupplied()).isEqualTo(expected);
    }
}
