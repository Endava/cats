package com.endava.cats.command.triage;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RemediationsTest {
    @ParameterizedTest
    @CsvSource({
            "InvalidValuesInEnumsFieldsFuzzer,--allowInvalidEnumValues",
            "EnumCaseVariantFieldsFuzzer,--no-allowDifferentCaseEnumValues",
            "HomoglyphEnumFieldsFuzzer,look-alike or ordinal",
            "NumericStringsInNumericFieldsFuzzer,--no-strictTypes",
            "OtherFuzzer,Enforce input validation"
    })
    void shouldRecommendFuzzerSpecificValidation(String fuzzer, String expected) {
        assertThat(Remediations.validationBypass(test(fuzzer, "4XX", 200))).contains(expected, "POST /items");
    }

    @Test
    void shouldExplainRequiredFieldBypass() {
        TriageTestCase test = test("OtherFuzzer", "4XX", 200);
        test.testCase().setScenario("field is required [TRUE]");
        assertThat(Remediations.validationBypass(test)).contains("remove it from `required`", "correlation rule");
    }

    @ParameterizedTest
    @CsvSource({"true,2XX,404,add it", "false,2XX,400,schema-valid request", "false,2XX,200,align the service", "false,4XX,200,align the service"})
    void shouldExplainStatusMismatch(boolean undocumented, String expected, int status, String recommendation) {
        assertThat(Remediations.contractMismatch(test("Fuzzer", expected, status), undocumented)).contains(recommendation, "POST /items");
    }

    @ParameterizedTest
    @CsvSource(value = {"REMOVE||always|omit child", "REQUIRE|kind == 'x'|when kind == 'x'|set child"}, delimiter = '|')
    void shouldDescribeSynthesizedRule(String action, String when, String expectedWhen, String expectedAction) {
        TriageTestCase test = test("Fuzzer", "2XX", 400);
        var rule = new SynthesizedCorrelationRule("/items", when, null, null, action, "child", "value", "invalid");
        var context = new ClassificationContext(Set.of(), Map.of(), Map.of(), Map.of(1, rule), Set.of(), null, null);
        assertThat(Remediations.crossField(test, new ErrorMessage("invalid", "invalid", false), context).remediation())
                .contains(expectedWhen, expectedAction, "--export-correlations");
    }

    @Test
    void shouldRecommendRefDataWhenNoRuleExists() {
        var context = new ClassificationContext(Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), null, null);
        var message = new ErrorMessage("invalid", "invalid", false);
        assertThat(Remediations.crossField(test("Fuzzer", "2XX", 400), message, context).remediation()).contains("--refData", "--correlations");
        assertThat(Remediations.fieldDomain(test("Fuzzer", "2XX", 400), "parent.child", message).remediation()).contains("parent#child", "--refData");
    }

    private TriageTestCase test(String fuzzer, String expected, int status) {
        return TriageFixtures.rejected(fuzzer, expected, "/items", "{}", status, "invalid");
    }
}
