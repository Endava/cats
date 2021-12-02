package com.endava.cats.args;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsSkipped;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(value = {FilterArguments.class, CheckArguments.class, IgnoreArguments.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FilterArgumentsTest {

    @Autowired
    private FilterArguments filterArguments;

    @Autowired
    private CheckArguments checkArguments;

    @Autowired
    private IgnoreArguments ignoreArguments;

    @ParameterizedTest
    @CsvSource({"checkHeaders,CheckSecurityHeadersFuzzer,RemoveFieldsFuzzer",
            "checkFields,RemoveFieldsFuzzer,CheckSecurityHeadersFuzzer",
            "checkHttp,HappyFuzzer,CheckSecurityHeadersFuzzer",
            "checkContract,TopLevelElementsContractInfoFuzzer,CheckSecurityHeadersFuzzer"})
    void shouldReturnCheckHeadersFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(checkArguments, argument, true);
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }


    @ParameterizedTest
    @CsvSource({"includeControlChars,LeadingControlCharsInHeadersFuzzer,LeadingWhitespacesInHeadersFuzzer",
            "includeEmojis,LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer",
            "includeWhitespaces,LeadingWhitespacesInFieldsTrimValidateFuzzer,LeadingControlCharsInHeadersFuzzer"})
    void shouldIncludeLengthyFuzzers(String argument, String matching, String notMatching) {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        ReflectionTestUtils.setField(checkArguments, argument, true);

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }

    @Test
    void shouldIncludeAllFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", true);
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", true);
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", true);
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("LeadingControlCharsInHeadersFuzzer", "LeadingWhitespacesInHeadersFuzzer", "LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer"
                , "RemoveFieldsFuzzer", "CheckSecurityHeadersFuzzer").hasSize(78);
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
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2xx"));

        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldNotReturnContractFuzzersWhenBlackbox() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);

        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("TopLevelElementsContractInfoFuzzer");
    }

    @Test
    void shouldRemoveSkippedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("CheckSecurityHeadersFuzzer", "VeryLargeValuesInFieldsFuzzer", "Jumbo");

    }

    @Test
    void shouldOnlyIncludeSuppliedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", List.of("VeryLarge", "SecurityHeaders", "Jumbo"));
        filterArguments.loadConfig();
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).doesNotContain("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer", "Jumbo")
                .containsOnly("CheckSecurityHeadersFuzzer", "VeryLargeValuesInFieldsFuzzer", "VeryLargeUnicodeValuesInFieldsFuzzer", "VeryLargeUnicodeValuesInHeadersFuzzer", "VeryLargeValuesInHeadersFuzzer");
    }

    @Test
    void givenAContractAndAServerAndASkipFuzzerArgument_whenStartingCats_thenTheSkipForIsCorrectlyProcessed() {
        filterArguments.loadConfig("--contract=src/test/resources/petstore.yml", "--server=http://localhost:8080", "--skipVeryLargeValuesInFieldsFuzzerForPath=/pets");
        Assertions.assertThat(filterArguments.skipFuzzersForPaths)
                .containsOnly(CatsSkipped.builder().fuzzer("VeryLargeValuesInFieldsFuzzer").forPaths(Collections.singletonList("/pets")).build());
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
}
