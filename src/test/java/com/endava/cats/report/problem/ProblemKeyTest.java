package com.endava.cats.report.problem;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static com.endava.cats.report.problem.ProblemFixtures.test;

@QuarkusTest
class ProblemKeyTest {
    static final OpenAPI CONTRACT = new OpenAPIV3Parser().readContents("""
            openapi: 3.0.0
            info: {title: t, version: '1'}
            paths:
              /risk-rules:
                post:
                  responses:
                    '201': {description: created, content: {application/json: {schema: {$ref: '#/components/schemas/RuleResponse'}}}}
                get:
                  responses:
                    '200': {description: ok, content: {application/json: {schema: {type: array, items: {$ref: '#/components/schemas/RuleResponse'}}}}}
              /risk-rules/{ruleId}:
                put:
                  responses:
                    '2XX': {description: ok, content: {application/json: {schema: {$ref: '#/components/schemas/RuleResponse'}}}}
              /inline:
                get:
                  responses:
                    '200': {description: ok, content: {application/json: {schema: {type: object}}}}
            components:
              schemas:
                RuleResponse: {type: object}
            """).getOpenAPI();
    static final ResponseSchemaNames SCHEMAS = new ResponseSchemaNames(CONTRACT);

    @Test
    void shouldKeyOneCrashUnderFourReasonsAsOneProblem() {
        List<CatsTestCaseSummary> tests = List.of(
                test(1).error().reason("Unexpected response code: 500", 500).template("aaaa1111", "NPE @ Foo.bar").build(),
                test(2).error().reason("Unexpected behaviour 500", 500).template("aaaa1111", "NPE @ Foo.bar").build(),
                test(3).error().reason("Server error", 500).template("aaaa1111", "NPE @ Foo.bar").build(),
                test(4).error().reason("Missing recommended security headers", 500).template("aaaa1111", "NPE @ Foo.bar").build());

        List<Problem> problems = ProblemCatalog.build(tests, SCHEMAS);

        Assertions.assertThat(problems).singleElement().satisfies(problem -> {
            Assertions.assertThat(problem.kind()).isEqualTo(ProblemKind.SERVER_ERROR);
            Assertions.assertThat(problem.reasons()).hasSize(4);
            Assertions.assertThat(problem.errorTemplate()).isEqualTo("NPE @ Foo.bar");
            Assertions.assertThat(problem.title()).isEqualTo("Server error (HTTP 500): NPE @ Foo.bar");
        });
    }

    @Test
    void shouldKeyAcceptedRequestsByFuzzerAndStatusFamily() {
        List<CatsTestCaseSummary> tests = List.of(
                test(1).reason("Unexpected response code: 200", 200).fuzzer("NumericValuesInStringFields").build(),
                test(2).reason("Unexpected response code: 200", 200).fuzzer("ZeroWidthCharsInNamesFields").build(),
                test(3).reason("Unexpected response code: 200", 200).fuzzer("UnsupportedAcceptHeaders").build(),
                test(4).reason("Unexpected response code: 201", 201).fuzzer("LeadingZeros").build(),
                test(5).reason("Unexpected behaviour 202", 202).fuzzer("LeadingZeros").operation("PUT", "/orders/{id}").build());

        List<Problem> problems = ProblemCatalog.build(tests, SCHEMAS);

        Assertions.assertThat(problems).hasSize(4).allMatch(problem -> problem.kind() == ProblemKind.ACCEPTED_REQUEST);
        Assertions.assertThat(problems).filteredOn(problem -> problem.testCount() == 2).singleElement().satisfies(problem -> {
            Assertions.assertThat(problem.title()).isEqualTo("Leading zeros accepted (HTTP 2xx)");
            Assertions.assertThat(problem.occurrences()).hasSize(2);
            Assertions.assertThat(problem.errorTemplateId()).isNull();
        });
    }

    @Test
    void shouldKeySchemaMismatchesByTheDeclaredSchemaWithTheViolationsAsVariants() {
        String velocity = "Response does NOT match expected result. Validation errors: $: no matching schema found for discriminator value 'VELOCITY'";
        String nulls = "Response does NOT match expected result. Validation errors: $.name: null found, string expected";
        List<CatsTestCaseSummary> tests = List.of(
                test(1).reason("Not matching response schema", 201).operation("POST", "/risk-rules").details(velocity).build(),
                test(2).reason("Not matching response schema", 201).operation("POST", "/risk-rules").details(velocity).build(),
                test(3).reason("Not matching response schema", 200).operation("PUT", "/risk-rules/{ruleId}").details(nulls).build(),
                test(4).reason("Not matching response schema", 200).operation("GET", "/risk-rules").details(nulls).build(),
                test(5).reason("Not matching response schema", 200).operation("GET", "/inline").details(nulls).build());

        List<Problem> problems = ProblemCatalog.build(tests, SCHEMAS);

        Assertions.assertThat(problems).extracting(Problem::title).containsExactlyInAnyOrder(
                "Response does not match the schema RuleResponse", "Response does not match the schema RuleResponse[]",
                "Response does not match the schema GET /inline 200");
        Assertions.assertThat(problems).filteredOn(problem -> problem.testCount() == 3).singleElement().satisfies(problem -> {
            Assertions.assertThat(problem.variants()).extracting(Problem.Variant::count).containsExactly(2, 1);
            Assertions.assertThat(problem.variants().getFirst().text()).isEqualTo("$: no matching schema found for discriminator value 'VELOCITY'");
            Assertions.assertThat(problem.evidence().testId()).isEqualTo("Test 1");
        });
    }

    @Test
    void shouldMergeTheGenericStatusVerdictsOfOneServerMessage() {
        List<CatsTestCaseSummary> tests = List.of(
                test(1).error().reason("Unexpected response code: 422", 422).template("bbbb2222", "Field is required").build(),
                test(2).error().reason("Unexpected behaviour 422", 422).template("bbbb2222", "Field is required").build(),
                test(3).error().reason("Not found", 404).template("cccc3333", "Not found").build());

        List<Problem> problems = ProblemCatalog.build(tests, SCHEMAS);

        Assertions.assertThat(problems).extracting(Problem::title)
                .containsExactly("Request rejected (HTTP 422): Field is required", "Not found (HTTP 404): Not found");
    }

    @Test
    void shouldKeyStatusCodeProblemsByStatusWithTheServerMessagesAsVariants() {
        List<CatsTestCaseSummary> tests = List.of(
                test(1).reason("Undocumented response code: 422", 422).template("dddd4444", "size must be between NUM and NUM").build(),
                test(2).reason("Undocumented response code: 422", 422).template("eeee5555", "must not be null").operation("GET", "/a").build(),
                test(3).reason("Undocumented response code: 422", 422).template("dddd4444", "size must be between NUM and NUM").build(),
                test(4).error().reason("Unexpected response code: 422", 422).template("dddd4444", "size must be between NUM and NUM").build());

        List<Problem> problems = ProblemCatalog.build(tests, SCHEMAS);

        Problem status = problems.stream().filter(problem -> problem.kind() == ProblemKind.STATUS_CODE).findFirst().orElseThrow();
        Problem message = problems.stream().filter(problem -> problem.kind() == ProblemKind.ERROR_MESSAGE).findFirst().orElseThrow();
        Assertions.assertThat(status.title()).isEqualTo("Undocumented response code: 422");
        Assertions.assertThat(status.variants()).containsExactly(new Problem.Variant("dddd4444", "size must be between NUM and NUM", 2),
                new Problem.Variant("eeee5555", "must not be null", 1));
        Assertions.assertThat(status.errorTemplate()).isNull();
        Assertions.assertThat(status.alsoIn()).containsExactly(message.id());
        Assertions.assertThat(message.alsoIn()).containsExactly(status.id());
        Assertions.assertThat(problems.getFirst()).isEqualTo(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Response content type not matching the contract: expected [application/json], actual [text/html;charset=utf-8]|text/html",
            "Missing recommended Security Headers: [X-Frame-Options=DENY]|Missing recommended Security Headers: [X-Frame-Options=DENY]",
            "The following response headers defined in the contract are missing: [X-Rate-Limit-42]|The following response headers defined in the contract are missing: [X-Rate-Limit-N]"
    })
    void shouldKeyOtherViolationsByTheMaskedViolation(String details, String violation) {
        Assertions.assertThat(ProblemKey.of(test(1).reason("Response content type not matching the contract", 400).details(details).build(), SCHEMAS).parts())
                .containsExactly("Response content type not matching the contract", violation);
    }

    @Test
    void shouldComputeVersionedEightHexIds() {
        ProblemKey key = new ProblemKey(ProblemKind.SERVER_ERROR, List.of("aaaa1111"));

        Assertions.assertThat(key.id()).matches("[0-9a-f]{8}").isEqualTo(new ProblemKey(ProblemKind.SERVER_ERROR, List.of("aaaa1111")).id())
                .isNotEqualTo(new ProblemKey(ProblemKind.ERROR_MESSAGE, List.of("aaaa1111")).id());
    }

    @Test
    void shouldRecomputeThePersistedProblemIdAfterAJsonRoundTrip() {
        List<CatsTestCaseSummary> tests = mixedRun();
        ProblemCatalog.build(tests, SCHEMAS);

        tests.forEach(original -> {
            CatsTestCaseSummary readBack = JsonUtils.GSON.fromJson(JsonUtils.GSON.toJson(original), CatsTestCaseSummary.class);
            Assertions.assertThat(ProblemKey.of(readBack, SCHEMAS).id()).isEqualTo(readBack.getProblemId()).isEqualTo(original.getProblemId());
        });
    }

    @Test
    void shouldGiveTheSameProblemsWhateverTheOrderOfTheTests() {
        List<Problem> expected = ProblemCatalog.build(mixedRun(), SCHEMAS);

        IntStream.range(0, 5).forEach(seed -> {
            List<CatsTestCaseSummary> shuffled = new ArrayList<>(mixedRun());
            Collections.shuffle(shuffled, new Random(seed));
            Assertions.assertThat(ProblemCatalog.build(shuffled, SCHEMAS)).isEqualTo(expected);
        });
    }

    static List<CatsTestCaseSummary> mixedRun() {
        return List.of(
                test(1).error().reason("Unexpected response code: 500", 500).template("aaaa1111", "NPE").build(),
                test(2).error().reason("Server error", 500).template("aaaa1111", "NPE").operation("GET", "/b").build(),
                test(3).reason("XSS payload reflected in response", 201).fuzzer("XssInjectionInStringFields").bodyFields("name").build(),
                test(4).reason("XSS payload accepted without validation", 201).fuzzer("XssInjectionInStringFields").bodyFields("name").build(),
                test(5).reason("Not matching response schema", 201).operation("POST", "/risk-rules").details("Validation errors: $.a: null").build(),
                test(6).reason("Undocumented response code: 422", 422).template("bbbb2222", "bad").build(),
                test(7).reason("Unexpected response code: 200", 200).fuzzer("LeadingZeros").queryField("q").build(),
                test(8).error().reason("Connection refused", 953).build(),
                test(9).error().reason("Unexpected response code: 422", 422).template("bbbb2222", "bad").build());
    }
}
