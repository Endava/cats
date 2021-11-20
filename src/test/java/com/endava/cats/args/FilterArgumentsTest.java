package com.endava.cats.args;

import com.endava.cats.CatsMain;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsSkipped;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig({FilterArguments.class, CheckArguments.class, CatsMain.class})
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FilterArgumentsTest {

    @Autowired
    private FilterArguments filterArguments;

    @Autowired
    private CheckArguments checkArguments;


    @ParameterizedTest
    @CsvSource({"checkHeaders,CheckSecurityHeadersFuzzer,RemoveFieldsFuzzer",
            "checkFields,RemoveFieldsFuzzer,CheckSecurityHeadersFuzzer",
            "checkHttp,HappyFuzzer,CheckSecurityHeadersFuzzer",
            "checkContract,TopLevelElementsContractInfoFuzzer,CheckSecurityHeadersFuzzer"})
    void shouldReturnCheckHeadersFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, argument, "true");
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }


    @ParameterizedTest
    @CsvSource({"includeControlChars,LeadingControlCharsInHeadersFuzzer,LeadingWhitespacesInHeadersFuzzer",
            "includeEmojis,LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeWhitespaces,LeadingWhitespacesInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer"})
    void shouldIncludeLengthyFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", "empty");
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", "empty");
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", "empty");
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        ReflectionTestUtils.setField(checkArguments, argument, "true");

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }

    @Test
    void shouldIncludeAllFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", "true");
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", "true");
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", "true");
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("LeadingControlCharsInHeadersFuzzer", "LeadingWhitespacesInHeadersFuzzer", "LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer"
                , "RemoveFieldsFuzzer", "CheckSecurityHeadersFuzzer").hasSize(76);
    }

    @Test
    void shouldReturnAllFuzzersWhenNoCheckSupplied() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("TopLevelElementsContractInfoFuzzer", "CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenIgnoredSupplied() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        ReflectionTestUtils.setField(filterArguments, "ignoreResponseCodes", "2xx");

        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldRemoveSkippedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", "VeryLarge, SecurityHeaders, Jumbo");
        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("CheckSecurityHeadersFuzzer", "VeryLargeStringsFuzzer", "Jumbo");

    }

    @Test
    void shouldOnlyIncludeSuppliedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", "VeryLarge, SecurityHeaders, Jumbo");
        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).doesNotContain("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer", "Jumbo")
                .containsOnly("CheckSecurityHeadersFuzzer", "VeryLargeStringsFuzzer");
    }

    @Test
    void givenAContractAndAServerAndASkipFuzzerArgument_whenStartingCats_thenTheSkipForIsCorrectlyProcessed() {
        filterArguments.loadConfig("--contract=src/test/resources/petstore.yml", "--server=http://localhost:8080", "--skipVeryLargeStringsFuzzerForPath=/pets");
        Assertions.assertThat(filterArguments.skipFuzzersForPaths)
                .containsOnly(CatsSkipped.builder().fuzzer("VeryLargeStringsFuzzer").forPaths(Collections.singletonList("/pets")).build());
    }

    @Test
    void shouldReturnAllHttpMethodsWhenNotHttpMethodSupplied() {
        ReflectionTestUtils.setField(filterArguments, "httpMethods", "empty");
        List<HttpMethod> httpMethods = filterArguments.getHttpMethods();

        Assertions.assertThat(httpMethods).containsExactlyElementsOf(HttpMethod.restMethods());
    }

    @Test
    void shouldReturnGetAndDeleteWhenNotHttpMethodSupplied() {
        ReflectionTestUtils.setField(filterArguments, "httpMethods", "GET,DELETE");
        List<HttpMethod> httpMethods = filterArguments.getHttpMethods();

        Assertions.assertThat(httpMethods).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "'", "'   '", "empty"}, nullValues = {"null"})
    void shouldReturnFalseForProvidedIgnoredCodes(String codes) {
        ReflectionTestUtils.setField(filterArguments, "ignoreResponseCodes", codes);

        Assertions.assertThat(filterArguments.isEmptyIgnoredResponseCodes()).isTrue();
    }

    @Test
    void shouldReturnIgnoreHttpCodes() {
        ReflectionTestUtils.setField(filterArguments, "ignoreResponseCodes", "200,4XX");
        List<String> ignoredCodes = filterArguments.getIgnoreResponseCodes();

        Assertions.assertThat(ignoredCodes).containsOnly("200", "4XX");
    }

    @Test
    void shouldReturnEmptyIgnoreHttpCodes() {
        ReflectionTestUtils.setField(filterArguments, "ignoreResponseCodes", "empty");
        List<String> ignoredCodes = filterArguments.getIgnoreResponseCodes();

        Assertions.assertThat(ignoredCodes).isEmpty();
    }

    @Test
    void shouldReturnNotPresentTestCases() {
        ReflectionTestUtils.setField(filterArguments, "testCases", "empty");
        boolean testCasesPresent = filterArguments.areTestCasesSupplied();

        Assertions.assertThat(testCasesPresent).isFalse();
    }

    @Test
    void shouldReturnTestCases() {
        ReflectionTestUtils.setField(filterArguments, "testCases", "Test10,Test12.json");
        boolean testCasesPresent = filterArguments.areTestCasesSupplied();

        Assertions.assertThat(testCasesPresent).isTrue();
    }

    @Test
    void shouldParseTestCases() {
        ReflectionTestUtils.setField(filterArguments, "testCases", "Test10,Test12.json");
        List<String> testCases = filterArguments.parseTestCases();

        Assertions.assertThat(testCases).contains("cats-report/Test10.json", "Test12.json");
    }

    @Test
    void shouldMatchIgnoredResponseCodes() {
        ReflectionTestUtils.setField(filterArguments, "ignoreResponseCodes", "2XX,400");
        Assertions.assertThat(filterArguments.isIgnoredResponseCode("200")).isTrue();
        Assertions.assertThat(filterArguments.isIgnoredResponseCode("202")).isTrue();
        Assertions.assertThat(filterArguments.isIgnoredResponseCode("400")).isTrue();
        Assertions.assertThat(filterArguments.isIgnoredResponseCode("404")).isFalse();
    }

}
