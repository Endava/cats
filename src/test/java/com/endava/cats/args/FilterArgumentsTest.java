package com.endava.cats.args;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.fields.UserDictionaryFieldsFuzzer;
import com.endava.cats.fuzzer.headers.UserDictionaryHeadersFuzzer;
import com.endava.cats.fuzzer.http.CheckDeletedResourcesNotAvailableFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
        ReflectionTestUtils.setField(filterArguments, "processingArguments", processingArguments);
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "skipFields", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "paths", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "fuzzersToBeRunComputed", false);

        FilterArguments.ALL_CATS_FUZZERS.clear();
        FilterArguments.FUZZERS_TO_BE_RUN.clear();
        FilterArguments.PATHS_TO_INCLUDE.clear();
        filterArguments.getUserArguments().words = null;
    }

    @ParameterizedTest
    @CsvSource({"checkHeaders,CheckSecurityHeadersFuzzer,RemoveFieldsFuzzer",
            "checkFields,RemoveFieldsFuzzer,CheckSecurityHeadersFuzzer",
            "checkHttp,HappyPathFuzzer,CheckSecurityHeadersFuzzer", "includeControlChars,LeadingControlCharsInHeadersFuzzer,LeadingWhitespacesInHeadersFuzzer",
            "includeEmojis,LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeWhitespaces,LeadingWhitespacesInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeContract,TopLevelElementsLinterFuzzer,LeadingWhitespacesInHeadersFuzzer"})
    void shouldReturnCheckHeadersFuzzers(String argument, String matching, String notMatching) {
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
                , "RemoveFieldsFuzzer", "CheckSecurityHeadersFuzzer").hasSize(144);
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
                .doesNotContain("TopLevelElementsLinterFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);

        List<String> fuzzers = filterArguments.getFirstPhaseFuzzersForPath();

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyPathFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsLinterFuzzer");
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

        Assertions.assertThat(fuzzers).doesNotContain("TopLevelElementsLinterFuzzer", "HappyPathFuzzer", "RemoveFieldsFuzzer", "Jumbo")
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
        Assertions.assertThat(filterArguments.getAllRegisteredFuzzers()).hasSize(149);
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
        ReflectionTestUtils.setField(filterArguments, "fuzzersToBeRunComputed", true);

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
        Assertions.assertThat(filterArguments.getSecondPhaseFuzzers()).hasOnlyElementsOfType(CheckDeletedResourcesNotAvailableFuzzer.class);
    }

    @Test
    void shouldReturnEmptySecondPhaseWhenSpecialFuzzer() {
        Assertions.assertThat(filterArguments.getSecondPhaseFuzzers()).hasOnlyElementsOfType(CheckDeletedResourcesNotAvailableFuzzer.class);
        filterArguments.customFilter("FunctionalFuzzer");
        Assertions.assertThat(filterArguments.getSecondPhaseFuzzers()).isEmpty();
    }

    @Test
    void shouldOnlyReturnSpecialFuzzer() {
        filterArguments.customFilter("FunctionalFuzzer");
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).containsOnly("FunctionalFuzzer");
    }

    @Test
    void shouldHave4FieldTypes() {
        Assertions.assertThat(FilterArguments.FieldType.values()).hasSize(4);
    }

    @Test
    void shouldHave16FieldFormats() {
        Assertions.assertThat(FilterArguments.FormatType.values()).hasSize(16);
    }

    @Test
    void shouldReturnSkippedFields() {
        ReflectionTestUtils.setField(filterArguments, "skipFields", List.of("field1", "field2"));

        List<String> skipFields = filterArguments.getSkipFields();
        Assertions.assertThat(skipFields).containsOnly("field1", "field2");
    }

    @Test
    void shouldReturnSkippedFieldsForAllFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFields", List.of("field1", "!field2"));

        List<String> skipFields = filterArguments.getSkipFields();
        List<String> skipFieldsForAllFuzzers = filterArguments.getSkipFieldsToBeSkippedForAllFuzzers();
        Assertions.assertThat(skipFields).containsOnly("field1", "!field2");
        Assertions.assertThat(skipFieldsForAllFuzzers).containsOnly("field2");
    }

    @Test
    void shouldReturnEmptySkipFields() {
        Assertions.assertThat(filterArguments.getSkipFields()).isEmpty();
    }

    @Test
    void shouldReturnEmptySkipHeaders() {
        Assertions.assertThat(filterArguments.getSkipHeaders()).isEmpty();
    }

    @Test
    void shouldNotFilter() {
        Paths paths = new Paths();
        paths.addPathItem("/path1", new PathItem());
        paths.addPathItem("/path2", new PathItem());
        OpenAPI openAPI = Mockito.mock(OpenAPI.class);
        Mockito.when(openAPI.getPaths()).thenReturn(paths);

        List<String> filteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(filteredPaths).containsOnly("/path1", "/path2");
    }

    @Test
    void shouldCachePathsToRun() {
        Paths paths = new Paths();
        paths.addPathItem("/path1", new PathItem());
        paths.addPathItem("/path11", new PathItem());
        paths.addPathItem("/path2", new PathItem());
        ReflectionTestUtils.setField(filterArguments, "paths", List.of("/path1"));

        OpenAPI openAPI = Mockito.mock(OpenAPI.class);
        Mockito.when(openAPI.getPaths()).thenReturn(paths);

        List<String> filteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(filteredPaths).containsOnly("/path1");
        ReflectionTestUtils.setField(filterArguments, "paths", List.of("/path1", "/path11", "/path2"));

        List<String> secondTimeFilteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(secondTimeFilteredPaths).containsOnly("/path1");

        FilterArguments.PATHS_TO_INCLUDE.clear();
        List<String> thirdTimeFilteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(thirdTimeFilteredPaths).containsOnly("/path1", "/path11", "/path2");
    }

    @Test
    void shouldOnlyIncludeSuppliedPaths() {
        Paths paths = new Paths();
        paths.addPathItem("/path1", new PathItem());
        paths.addPathItem("/path11", new PathItem());
        paths.addPathItem("/path2", new PathItem());
        ReflectionTestUtils.setField(filterArguments, "paths", List.of("/path1"));

        OpenAPI openAPI = Mockito.mock(OpenAPI.class);
        Mockito.when(openAPI.getPaths()).thenReturn(paths);

        List<String> filteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(filteredPaths).containsOnly("/path1");
    }

    @Test
    void shouldExcludeSkipPaths() {
        Paths paths = new Paths();
        paths.addPathItem("/path1", new PathItem());
        paths.addPathItem("/path2", new PathItem());
        ReflectionTestUtils.setField(filterArguments, "skipPaths", List.of("/path1"));

        OpenAPI openAPI = Mockito.mock(OpenAPI.class);
        Mockito.when(openAPI.getPaths()).thenReturn(paths);

        List<String> filteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(filteredPaths).containsOnly("/path2");
    }

    @ParameterizedTest
    @CsvSource({"/path1*,/path11", "/path2*,/path2", "*path3,/another-path3", "*another*,/another-path3"})
    void shouldIncludeWildcard(String wildcardPattern, String result) {
        Paths paths = new Paths();
        paths.addPathItem("/path11", new PathItem());
        paths.addPathItem("/path2", new PathItem());
        paths.addPathItem("/another-path3", new PathItem());
        ReflectionTestUtils.setField(filterArguments, "paths", List.of(wildcardPattern));

        OpenAPI openAPI = Mockito.mock(OpenAPI.class);
        Mockito.when(openAPI.getPaths()).thenReturn(paths);

        List<String> filteredPaths = filterArguments.getPathsToRun(openAPI);
        Assertions.assertThat(filteredPaths).containsOnly(result);
    }

    @Test
    void shouldHaveDefaultHttpMethods() {
        Assertions.assertThat(filterArguments.isHttpMethodSupplied(HttpMethod.POST)).isTrue();
        Assertions.assertThat(filterArguments.isHttpMethodSupplied(HttpMethod.BIND)).isFalse();
    }

    @Test
    void shouldReturnFuzzersAsClasses() {
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersAsFuzzers()).hasSize(100);
    }

    @Test
    void shouldNotReturnAllFuzzers() {
        Set<HttpMethod> httpMethods = Set.of(HttpMethod.GET);
        List<Fuzzer> filteredFuzzers = filterArguments.filterOutFuzzersNotMatchingHttpMethods(httpMethods);
        int allFuzzersSize = filterArguments.getFirstPhaseFuzzersAsFuzzers().size();
        int filteredSize = filteredFuzzers.size();
        Assertions.assertThat(filteredSize).isNotEqualTo(allFuzzersSize);
    }

    @Test
    void shouldReturnAllFuzzers() {
        Set<HttpMethod> httpMethods = Set.of(HttpMethod.GET, HttpMethod.POST);
        List<Fuzzer> filteredFuzzers = filterArguments.filterOutFuzzersNotMatchingHttpMethods(httpMethods);
        int allFuzzersSize = filterArguments.getFirstPhaseFuzzersAsFuzzers().size();
        Assertions.assertThat(filteredFuzzers).hasSize(allFuzzersSize);
    }

    @Test
    void shouldSkipHttpMethods() {
        List<HttpMethod> skipped = List.of(HttpMethod.DELETE);
        filterArguments.setSkippedHttpMethods(skipped);

        Assertions.assertThat(filterArguments.getHttpMethods()).doesNotContain(HttpMethod.DELETE);
    }
}
