package com.endava.cats.command.triage;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@QuarkusTest
class CorrelationRuleSynthesizerTest {
    private static final String EXPECT_2XX = "Should return [2XX]";

    private static final String CONTRACT = """
            openapi: 3.0.0
            info: {title: t, version: "1"}
            paths:
              /plans:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema: {$ref: '#/components/schemas/Plan'}
                  responses:
                    "201": {description: ok}
            components:
              schemas:
                Plan:
                  type: object
                  properties:
                    planType: {type: string, enum: [RECURRING, ONE_TIME]}
                    seats: {type: integer, minimum: 5, maximum: 50}
                    tier: {type: string, enum: [GOLD, SILVER]}
                    tags: {type: array, items: {type: string}}
                    code: {type: string, maxLength: 4}
                    contactEmail: {type: string, format: email}
                    name: {type: string, minLength: 10, maxLength: 20}
            """;

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "Should return [2XX]|{\"sort\":\"HYI\"}|Invalid sort direction 'HYI'. Valid values: [DESC, ASC]",
            "Should return [4XX]|{\"type\":\"PREMIUM\"}|'discount' is required",
            "Should return [4XX|2XX]|{\"type\":\"PREMIUM\"}|'discount' is required",
            "Should return [2XX]|{\"type\":\"PREMIUM\"}|<html><body><p class=\"discount\">discount is required</p></body></html>"})
    void shouldNotSynthesizeFromValueErrorsNegativeTestsOrHtmlPages(String expected, String payload, String message) {
        TriageTestCase testCase = TriageFixtures.rejected("CustomFuzzer", expected, "/orders", payload, 422, message);

        Assertions.assertThat(synthesize(testCase, null)).isEmpty();
    }

    @Test
    void shouldPickRequiredFieldInsteadOfMentionedTriggerField() {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/orders",
                "{\"type\":\"PREMIUM\",\"name\":\"x\"}", 422, "When 'type' is 'PREMIUM', 'discount' is required");

        Assertions.assertThat(synthesize(testCase, null)).hasValueSatisfying(rule -> {
            Assertions.assertThat(rule.targetField()).isEqualTo("discount");
            Assertions.assertThat(rule.when()).isEqualTo("type == 'PREMIUM'");
            Assertions.assertThat(rule.value()).isEqualTo(PlaceholderValues.DEFAULT_STRING);
        });
    }

    @Test
    void shouldNotUseGeneratedFreeTextValuesAsTriggers() {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/orders",
                "{\"name\":\"ZFQUSJWPHE\",\"currency\":\"EUR\"}", 422, "'comment' must not be null");

        Assertions.assertThat(synthesize(testCase, null)).hasValueSatisfying(rule -> {
            Assertions.assertThat(rule.targetField()).isEqualTo("comment");
            Assertions.assertThat(rule.when()).isNull();
            Assertions.assertThat(rule.whenField()).isNull();
        });
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "{\"items\":[{\"sku\":\"A1\"}]}|items[0].quantity: must not be null|items[*].quantity",
            "{\"name\":\"\"}|Bad Request: name: must not be blank|name",
            "{\"order\":{\"note\":\"\"}}|The note is required|order.note"})
    void shouldResolveArrayIndicesFieldPrefixesAndStopWords(String payload, String message, String expectedField) {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/orders", payload, 400, message);

        Assertions.assertThat(synthesize(testCase, null)).map(SynthesizedCorrelationRule::targetField).hasValue(expectedField);
    }

    @Test
    void shouldSynthesizeUnconditionalOmitForUnknownFields() {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/orders",
                "{\"legacyField\":\"x\"}", 400, "Unrecognized field 'legacyField'");

        Assertions.assertThat(synthesize(testCase, null)).hasValueSatisfying(rule -> {
            Assertions.assertThat(rule.action()).isEqualTo("REMOVE");
            Assertions.assertThat(rule.when()).isNull();
            Assertions.assertThat(rule.value()).isNull();
        });
    }

    @Test
    void shouldSynthesizeSelfGuardedUniquenessRuleForHappyPathDuplicates() {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/plans",
                "{\"name\":\"Plan-1\",\"planType\":\"RECURRING\"}", 409, "Plan with name 'Plan-1' already exists");

        Assertions.assertThat(synthesize(testCase, contract())).hasValueSatisfying(rule -> {
            Assertions.assertThat(rule.action()).isEqualTo("UNIQUE");
            Assertions.assertThat(rule.when()).isEqualTo("name is not null");
            Assertions.assertThat(rule.value()).isEqualTo("#(alphanumeric(10))");
        });
    }

    @Test
    void shouldNotSynthesizeWhenServerRejectsValueTheFuzzerIsMutating() {
        TriageTestCase testCase = TriageFixtures.withMutationTargets(TriageFixtures.rejected("MinLengthExactValuesInStringFields",
                EXPECT_2XX, "/plans", "{\"name\":\"B\",\"type\":\"COUNTRY\"}", 422,
                "Watchlist with name 'B' and type COUNTRY already exists"), "name");
        FailureAnalysis analysis = new FailureAnalyzer(RequestSchemaLookup.none()).analyze(testCase).orElseThrow();

        Assertions.assertThat(analysis.protectedField()).hasValue("name");
        Assertions.assertThat(new CorrelationRuleSynthesizer(RequestSchemaLookup.none()).synthesize(testCase, analysis)).isEmpty();
    }

    @Test
    void shouldFlagMutatedFieldMentionedInDomainRejection() {
        TriageTestCase testCase = TriageFixtures.withMutationTargets(TriageFixtures.rejected("MaxLengthExactValuesInStringFields",
                EXPECT_2XX, "/plans", "{\"country\":\"AA\"}", 400, "Unknown country 'AA'"), "country");

        Assertions.assertThat(new FailureAnalyzer(RequestSchemaLookup.none()).analyze(testCase))
                .flatMap(FailureAnalysis::protectedField)
                .hasValue("country");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "'seats' is required|seats|INTEGER",
            "'tier' is required|tier|GOLD",
            "'tags' is required|tags|LIST",
            "'code' is required|code|#(alphanumeric(4))",
            "'contactEmail' is required|contactEmail|#(email)"})
    void shouldTypePlaceholdersFromTheContract(String message, String field, String expectedValue) {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/plans", "{\"planType\":\"RECURRING\"}", 422, message);

        Object value = synthesize(testCase, contract()).orElseThrow().value();

        Assertions.assertThat(synthesize(testCase, contract())).map(SynthesizedCorrelationRule::targetField).hasValue(field);
        switch (expectedValue) {
            case "INTEGER" -> Assertions.assertThat(value).isEqualTo(BigInteger.valueOf(5));
            case "LIST" -> Assertions.assertThat(value).isEqualTo(List.of(PlaceholderValues.DEFAULT_STRING));
            default -> Assertions.assertThat(value).isEqualTo(expectedValue);
        }
    }

    @Test
    void shouldTypePlaceholdersFromMessageAndRequestWithoutContract() {
        TriageTestCase numeric = TriageFixtures.rejected("EnumCaseVariantFields", EXPECT_2XX, "/rules",
                "{\"operator\":\"gt\",\"value\":[\"KP\"]}", 422, "Operator GT requires a numeric value");
        TriageTestCase children = TriageFixtures.rejected("EnumCaseVariantFields", EXPECT_2XX, "/rules",
                "{\"type\":\"noT\",\"children\":[{\"a\":1},{\"b\":2}]}", 422, "NOT expression must have exactly one child, found 2");

        Assertions.assertThat(synthesize(numeric, null)).hasValueSatisfying(rule -> {
            Assertions.assertThat(rule.when()).isEqualTo("operator == 'GT'");
            Assertions.assertThat(rule.value()).isEqualTo(1);
        });
        Assertions.assertThat(synthesize(children, null)).hasValueSatisfying(rule -> {
            Assertions.assertThat(rule.when()).isEqualTo("type == 'NOT'");
            Assertions.assertThat(rule.value()).asString().isEqualTo("[{\"a\":1}]");
        });
    }

    @Test
    void shouldRejectGuessedFieldsUnknownToTheContractAndCanonicalizeEnumTriggers() {
        TriageTestCase unknownField = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/plans",
                "{\"planType\":\"RECURRING\"}", 422, "The foo bar is required");
        TriageTestCase mentionedTrigger = TriageFixtures.rejected("HappyPath", EXPECT_2XX, "/plans",
                "{\"planType\":\"recurring\"}", 422, "seats is required for recurring plans");

        Assertions.assertThat(synthesize(unknownField, contract())).isEmpty();
        Assertions.assertThat(synthesize(mentionedTrigger, contract())).map(SynthesizedCorrelationRule::when)
                .hasValue("planType == 'RECURRING'");
    }

    private static Optional<SynthesizedCorrelationRule> synthesize(TriageTestCase testCase, OpenAPI openAPI) {
        RequestSchemaLookup lookup = new RequestSchemaLookup(openAPI);
        return new FailureAnalyzer(lookup).analyze(testCase)
                .flatMap(analysis -> new CorrelationRuleSynthesizer(lookup).synthesize(testCase, analysis));
    }

    private static OpenAPI contract() {
        return new OpenAPIParser().readContents(CONTRACT, null, null).getOpenAPI();
    }
}
