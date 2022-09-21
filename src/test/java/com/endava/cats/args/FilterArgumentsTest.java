package com.endava.cats.args;

import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@QuarkusTest
class FilterArgumentsTest {

    @Inject
    FilterArguments filterArguments;

    IgnoreArguments ignoreArguments;

    CheckArguments checkArguments;

    ProcessingArguments processingArguments;


    @BeforeEach
    void setup() {
        checkArguments = new CheckArguments();
        ignoreArguments = new IgnoreArguments();
        processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(filterArguments, "checkArguments", checkArguments);
        ReflectionTestUtils.setField(filterArguments, "ignoreArguments", ignoreArguments);
        ReflectionTestUtils.setField(filterArguments, "processingArguments", processingArguments);

        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", Collections.emptyList());
        FilterArguments.ALL_CATS_FUZZERS.clear();
        FilterArguments.FUZZERS_TO_BE_RUN.clear();
    }

    @ParameterizedTest
    @CsvSource({"checkHeaders,CheckSecurityHeadersFuzzer,RemoveFieldsFuzzer",
            "checkFields,RemoveFieldsFuzzer,CheckSecurityHeadersFuzzer",
            "checkHttp,HappyFuzzer,CheckSecurityHeadersFuzzer"})
    void shouldReturnCheckHeadersFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, argument, true);

        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }


    @ParameterizedTest
    @CsvSource({"includeControlChars,LeadingControlCharsInHeadersFuzzer,LeadingWhitespacesInHeadersFuzzer",
            "includeEmojis,LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeWhitespaces,LeadingWhitespacesInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeContract,TopLevelElementsContractInfoFuzzer,LeadingWhitespacesInHeadersFuzzer"})
    void shouldIncludeLengthyFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, argument, true);

        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }

    @Test
    void shouldIncludeAllFuzzers() {
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", true);
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", true);
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", true);
        ReflectionTestUtils.setField(checkArguments, "includeContract", true);
        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("LeadingControlCharsInHeadersFuzzer", "LeadingWhitespacesInHeadersFuzzer", "LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer"
                , "RemoveFieldsFuzzer", "CheckSecurityHeadersFuzzer").hasSize(93);
    }

    @Test
    void shouldReturnAllFuzzersWhenNoCheckSupplied() {
        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenIgnoredSupplied() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2xx"));

        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);

        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldRemoveSkippedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("CheckSecurityHeadersFuzzer", "VeryLargeStringsInFieldsFuzzer", "Jumbo");

    }

    @Test
    void shouldOnlyIncludeSuppliedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        List<String> fuzzers = filterArguments.getFuzzersForPath();

        Assertions.assertThat(fuzzers).doesNotContain("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer", "Jumbo")
                .containsOnly("CheckSecurityHeadersFuzzer", "VeryLargeStringsInFieldsFuzzer", "VeryLargeUnicodeStringsInFieldsFuzzer", "VeryLargeUnicodeStringsInHeadersFuzzer", "VeryLargeStringsInHeadersFuzzer",
                        "VeryLargeDecimalsInNumericFieldsFuzzer", "VeryLargeIntegersInNumericFieldsFuzzer");
    }

    @Test
    void shouldReturnAllHttpMethodsWhenNotHttpMethodSupplied() {
        List<HttpMethod> httpMethods = filterArguments.getHttpMethods();

        Assertions.assertThat(httpMethods).containsExactlyElementsOf(HttpMethod.restMethods());
    }

    @Test
    void shouldReturnGetAndDeleteWhenNotHttpMethodSupplied() {
        ReflectionTestUtils.setField(filterArguments, "httpMethods", List.of(HttpMethod.GET, HttpMethod.DELETE));
        List<HttpMethod> httpMethods = filterArguments.getHttpMethods();

        Assertions.assertThat(httpMethods).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void shouldReturnAllRegisteredFuzzers() {
        Assertions.assertThat(filterArguments.getAllRegisteredFuzzers()).hasSize(96);
    }

    @Test
    void shouldReturnEmptySkippedPaths() {
        Assertions.assertThat(filterArguments.getSkipPaths()).isEmpty();
    }

    @Test
    void shouldReturnEmptyPaths() {
        Assertions.assertThat(filterArguments.getPaths()).isEmpty();
    }

    @Test
    void shouldSetAllFuzzersToCustomFuzzer() {
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        Assertions.assertThat(filterArguments.getFuzzersForPath()).contains("VeryLargeUnicodeStringsInFieldsFuzzer");
        FilterArguments.FUZZERS_TO_BE_RUN.clear();
        filterArguments.customFilter("FunctionalFuzzer");
        Assertions.assertThat(filterArguments.getFuzzersForPath()).doesNotContain("VeryLargeUnicodeStringsInFieldsFuzzer");
    }
}
