package com.endava.cats.args;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.fields.UserDictionaryFieldsFuzzer;
import com.endava.cats.fuzzer.headers.UserDictionaryHeadersFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;

@QuarkusTest
class FilterArgumentsTest {

    @Inject
    FilterArguments filterArguments;

    IgnoreArguments ignoreArguments;

    CheckArguments checkArguments;

    ProcessingArguments processingArguments;

    @Inject
    Instance<Fuzzer> fuzzers;


    @BeforeEach
    void setup() {
        checkArguments = new CheckArguments();
        ignoreArguments = new IgnoreArguments();
        processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(filterArguments, "checkArguments", checkArguments);
        ReflectionTestUtils.setField(filterArguments, "processingArguments", processingArguments);
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", Collections.emptyList());
        FilterArguments.ALL_CATS_FUZZERS.clear();
        FilterArguments.FUZZERS_TO_BE_RUN.clear();
        filterArguments.getUserArguments().words = null;
    }

    @ParameterizedTest
    @CsvSource({"checkHeaders,CheckSecurityHeadersFuzzer,RemoveFieldsFuzzer",
            "checkFields,RemoveFieldsFuzzer,CheckSecurityHeadersFuzzer",
            "checkHttp,HappyPathFuzzer,CheckSecurityHeadersFuzzer"})
    void shouldReturnCheckHeadersFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, argument, true);

        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }


    @ParameterizedTest
    @CsvSource({"includeControlChars,LeadingControlCharsInHeadersFuzzer,LeadingWhitespacesInHeadersFuzzer",
            "includeEmojis,LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeWhitespaces,LeadingWhitespacesInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeContract,TopLevelElementsContractInfoFuzzer,LeadingWhitespacesInHeadersFuzzer"})
    void shouldIncludeLengthyFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, argument, true);

        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }

    @Test
    void shouldIncludeAllFuzzers() {
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", true);
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", true);
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", true);
        ReflectionTestUtils.setField(checkArguments, "includeContract", true);
        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("LeadingControlCharsInHeadersFuzzer", "LeadingWhitespacesInHeadersFuzzer", "LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer"
                , "RemoveFieldsFuzzer", "CheckSecurityHeadersFuzzer").hasSize(98);
    }

    @Test
    void shouldReturnAllFuzzersWhenNoCheckSupplied() {
        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyPathFuzzer", "RemoveFieldsFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenIgnoredSupplied() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2xx"));

        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyPathFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);

        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyPathFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldRemoveSkippedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("HappyPathFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("CheckSecurityHeadersFuzzer", "VeryLargeStringsInFieldsFuzzer", "Jumbo");

    }

    @Test
    void shouldOnlyIncludeSuppliedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).doesNotContain("TopLevelElementsContractInfoFuzzer", "HappyPathFuzzer", "RemoveFieldsFuzzer", "Jumbo")
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
        Assertions.assertThat(filterArguments.getAllRegisteredFuzzers()).hasSize(102);
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
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).contains("VeryLargeUnicodeStringsInFieldsFuzzer");
        FilterArguments.FUZZERS_TO_BE_RUN.clear();
        filterArguments.customFilter("FunctionalFuzzer");
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).doesNotContain("VeryLargeUnicodeStringsInFieldsFuzzer");
    }

    @Test
    void shouldOnlyReturnUserDictionaryFuzzersWhenWordsSupplied() {
        filterArguments.getUserArguments().setWords(new File("src/test/resources/headers/yml"));
        List<String> fuzzerList = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzerList).containsOnly(UserDictionaryFieldsFuzzer.class.getSimpleName(), UserDictionaryHeadersFuzzer.class.getSimpleName());
    }

    @Test
    void shouldNotGetFuzzersWhenAllFuzzersPopulated() {
        HappyPathFuzzer happyPathFuzzer = new HappyPathFuzzer(null);
        FilterArguments.ALL_CATS_FUZZERS.add(happyPathFuzzer);

        Assertions.assertThat(filterArguments.getAllRegisteredFuzzers()).containsOnly(happyPathFuzzer);
    }

    @Test
    void shouldNotGetFuzzersToBeRunWhenPopulated() {
        FilterArguments.FUZZERS_TO_BE_RUN.add("HappyPathFuzzer");

        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).containsOnly("HappyPathFuzzer");
    }

    @Test
    void shouldRemoveValidateAndSanitizeFuzzers() {
        ReflectionTestUtils.setField(processingArguments, "sanitizationStrategy", ProcessingArguments.SanitizationStrategy.SANITIZE_AND_VALIDATE);
        Assertions.assertThat(filterArguments.removeBasedOnSanitizationStrategy(List.of("AbugidasInStringFieldsSanitizeValidateFuzzer", "AbugidasInStringFieldsValidateSanitizeFuzzer")))
                .containsOnly("AbugidasInStringFieldsSanitizeValidateFuzzer");
    }

    @Test
    void shouldRemoveSanitizeAndValidateFuzzers() {
        ReflectionTestUtils.setField(processingArguments, "sanitizationStrategy", ProcessingArguments.SanitizationStrategy.VALIDATE_AND_SANITIZE);
        Assertions.assertThat(filterArguments.removeBasedOnSanitizationStrategy(List.of("AbugidasInStringFieldsSanitizeValidateFuzzer", "AbugidasInStringFieldsValidateSanitizeFuzzer")))
                .containsOnly("AbugidasInStringFieldsValidateSanitizeFuzzer");
    }

    @Test
    void shouldRemoveValidateAndTrimFuzzers() {
        ReflectionTestUtils.setField(processingArguments, "edgeSpacesStrategy", ProcessingArguments.TrimmingStrategy.TRIM_AND_VALIDATE);
        Assertions.assertThat(filterArguments.removeBasedOnTrimStrategy(List.of("LeadingControlCharsInFieldsTrimValidateFuzzer", "LeadingControlCharsInFieldsValidateTrimFuzzer")))
                .containsOnly("LeadingControlCharsInFieldsTrimValidateFuzzer");
    }

    @Test
    void shouldRemoveTrimAndValidateFuzzers() {
        ReflectionTestUtils.setField(processingArguments, "edgeSpacesStrategy", ProcessingArguments.TrimmingStrategy.VALIDATE_AND_TRIM);
        Assertions.assertThat(filterArguments.removeBasedOnTrimStrategy(List.of("LeadingControlCharsInFieldsTrimValidateFuzzer", "LeadingControlCharsInFieldsValidateTrimFuzzer")))
                .containsOnly("LeadingControlCharsInFieldsValidateTrimFuzzer");
    }

    @Test
    void shouldNotAddSecondPhaseFuzzersInFirstPhase() {
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).doesNotContain("CheckDeletedResourcesNotAvailableFuzzer");
    }

    @Test
    void shouldNotAddFirstPhaseFuzzersInSecondPhase() {
        Assertions.assertThat(filterArguments.getSecondPhaseFuzzers()).containsOnly("CheckDeletedResourcesNotAvailableFuzzer");
    }

    @Test
    void shouldReturnEmptySecondPhaseWhenSpecialFuzzer() {
        Assertions.assertThat(filterArguments.getSecondPhaseFuzzers()).containsOnly("CheckDeletedResourcesNotAvailableFuzzer");
        filterArguments.customFilter("FunctionalFuzzer");
        Assertions.assertThat(filterArguments.getSecondPhaseFuzzers()).isEmpty();
    }

    @Test
    void shouldOnlyReturnSpecialFuzzer() {
        filterArguments.customFilter("FunctionalFuzzer");
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).containsOnly("FunctionalFuzzer");
    }
}
