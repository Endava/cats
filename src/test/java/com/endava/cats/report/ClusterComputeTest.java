package com.endava.cats.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ErrorSignature;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ClusterComputeTest {

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldKeepNullAndEmptySummaryListsAsNoOps(List<CatsTestCaseSummary> summaries) {
        ClusterCompute.assignErrorTemplates(summaries);
        ClusterCompute.assignErrorTemplates(summaries, null);

        assertThat(templateGroupSizes(summaries)).isEmpty();
    }

    @Test
    void shouldIgnoreNullAndPassingSummariesWhenAssigningTemplates() {
        CatsTestCaseSummary error = createTestCaseSummary(400, "Bad Request", "error", "Invalid input");
        CatsTestCaseSummary success = createTestCaseSummary(400, "OK", "success", "Fine");

        ClusterCompute.assignErrorTemplates(Arrays.asList(null, error, success, null));

        assertThat(error.getErrorTemplateId()).isNotBlank();
        assertThat(success.getErrorTemplateId()).isNull();
    }

    @ParameterizedTest
    @MethodSource("similarAndDifferentErrors")
    void shouldShareTemplatesOnlyBetweenSimilarErrorsOfOneReason(List<CatsTestCaseSummary> testCases, List<Integer> expectedGroupSizes) {
        assertThat(templateGroupSizes(testCases)).containsExactlyElementsOf(expectedGroupSizes);
    }

    private static Stream<Arguments> similarAndDifferentErrors() {
        return Stream.of(
                Arguments.of(new ArrayList<CatsTestCaseSummary>(), List.of()),
                Arguments.of(List.of(
                        createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                        createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database")), List.of(1, 1)),
                Arguments.of(List.of(
                        createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                        createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /another/path/to/file")), List.of(2)),
                Arguments.of(List.of(
                        createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                        createTestCaseSummary(404, "Resource Missing", "error", "Error: File not found at /path/to/file")), List.of(1, 1)),
                Arguments.of(List.of(
                        createTestCaseSummary(404, "Not Found", "error", ""),
                        createTestCaseSummary(404, "Not Found", "error", ""),
                        createTestCaseSummary(404, "Not Found", "error", "   ")), List.of(3)),
                Arguments.of(List.of(
                        createTestCaseSummary(404, "Not Found", "error", null),
                        createTestCaseSummary(500, "Internal Server Error", "error", null)), List.of(1, 1)));
    }

    @Test
    void shouldMineOneTemplateForManySimilarErrors() {
        List<CatsTestCaseSummary> testCases = IntStream.range(0, 50)
                .mapToObj(i -> createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/" + i)).toList();

        assertThat(templateGroupSizes(testCases)).containsExactly(50);
    }

    @Test
    void shouldMineOneTemplatePerMessagePattern() {
        List<CatsTestCaseSummary> testCases = IntStream.range(0, 30).mapToObj(i -> createTestCaseSummary(404, "Not Found", "error", switch (i % 3) {
            case 0 -> "Error: File not found at /path/" + i;
            case 1 -> "Warning: Resource unavailable for id " + i;
            default -> "Exception: Database connection timeout after " + i + " seconds";
        })).toList();

        assertThat(templateGroupSizes(testCases)).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldGeneralizeALongVariableTokenOfAnOtherwiseIdenticalMessage() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(500, "Server Error", "error", "Error: " + "A".repeat(300) + " occurred at line 123"),
                createTestCaseSummary(500, "Server Error", "error", "Error: " + "A".repeat(300) + " occurred at line 456"),
                createTestCaseSummary(500, "Server Error", "error", "Error: " + "B".repeat(300) + " occurred at line 789"),
                createTestCaseSummary(500, "Server Error", "error", "Error: " + "B".repeat(300) + " failed after retrying twice"));

        assertThat(templateGroupSizes(testCases)).containsExactly(3, 1);
        assertThat(testCases.getFirst().getErrorTemplate()).isEqualTo("Error: <*> occurred at line NUM");
    }

    @Test
    void shouldSeparateDifferent500JsonExceptionsAndGroupIdenticalExceptionCauses() {
        String jacksonError1 = "{\"type\":\"http://localhost:8080/petclinic/api/owners/1\",\"title\":\"HttpMessageNotReadableException\",\"status\":500,\"detail\":\"JSON parse error: Cannot construct instance of OwnerDto\"}";
        String jacksonError2 = "{\"type\":\"http://localhost:8080/petclinic/api/pets/14\",\"title\":\"HttpMessageNotReadableException\",\"status\":500,\"detail\":\"JSON parse error: Cannot construct instance of OwnerDto\"}";
        String typeMismatch1 = "{\"type\":\"http://localhost:8080/petclinic/api/owners/-9223372036854775808\",\"title\":\"MethodArgumentTypeMismatchException\",\"status\":500,\"detail\":\"Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: '-9223372036854775808'\"}";
        String typeMismatch2 = "{\"type\":\"http://localhost:8080/petclinic/api/pets/2147483648\",\"title\":\"MethodArgumentTypeMismatchException\",\"status\":500,\"detail\":\"Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: '2147483648'\"}";
        String sqlIntegrity = "{\"type\":\"http://localhost:8080/petclinic/api/owners/1\",\"title\":\"DataIntegrityViolationException\",\"status\":500,\"detail\":\"could not execute statement [NULL not allowed for column 'TYPE_ID']\"}";
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(500, "Unexpected response code: 500", "error", jacksonError1),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", jacksonError2),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", typeMismatch1),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", typeMismatch2),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", sqlIntegrity),
                createTestCaseSummary(200, "Unexpected response code: 200", "error", "{\"id\":1,\"status\":\"accepted\"}"));

        assertThat(templateGroupSizes(testCases)).containsExactly(2, 2, 1);
        assertThat(testCases.get(4).getErrorTemplate()).contains("DataIntegrityViolationException");
        assertThat(testCases.getLast().getErrorTemplateId()).isNull();
    }

    @Test
    void shouldAssignStableTemplatesThatMaskEchoedRequestFields() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(400, "Bad Request", "error", "{\"detail\":\"name: must not be blank\"}", "{\"name\":\"\"}"),
                createTestCaseSummary(400, "Bad Request", "error", "{\"detail\":\"email: must not be blank\"}", "{\"email\":\"\"}"),
                createTestCaseSummary(200, "OK", "success", "{}", "{}"));

        ClusterCompute.assignErrorTemplates(testCases);

        assertThat(testCases.subList(0, 2)).extracting(CatsTestCaseSummary::getErrorTemplate)
                .containsOnly("<FIELD>: must not be blank");
        assertThat(testCases.getFirst().getErrorTemplateId()).isEqualTo(testCases.get(1).getErrorTemplateId());
        assertThat(testCases.getLast().getErrorTemplateId()).isNull();
    }

    @Test
    void shouldOnlyMineTemplatesForResponsesThatCarryAnErrorMessage() {
        CatsTestCaseSummary created = createTestCaseSummary(201, "XSS payload accepted without validation", "warn", "{\"name\":\"x\"}");
        CatsTestCaseSummary staleFromAnOlderReport = createTestCaseSummary(200, "Unexpected response code: 200", "warn", "{}");
        staleFromAnOlderReport.assignErrorTemplate("deadbeef", "<INPUT>: <INPUT>");
        CatsTestCaseSummary rejected = createTestCaseSummary(422, "Unexpected response code: 422", "error", "{\"message\":\"bad\"}");

        ClusterCompute.assignErrorTemplates(List.of(created, staleFromAnOlderReport, rejected));

        assertThat(List.of(created, staleFromAnOlderReport)).allSatisfy(test -> {
            assertThat(test.getErrorTemplateId()).isNull();
            assertThat(test.getErrorTemplate()).isNull();
        });
        assertThat(rejected.getErrorTemplateId()).isNotBlank();
        assertThat(rejected.getErrorTemplate()).isEqualTo("bad");
    }

    @Test
    void shouldPreferTheSignatureOfTheFullResponseBody() {
        CatsTestCaseSummary summary = createTestCaseSummary(500, "Server Error", "error", "Traceback (most recent call last):");
        summary.attachErrorSignature(new ErrorSignature("", List.of("KeyError:", "'owner'"), "owner_id@/app/app.py:50"));

        ClusterCompute.assignErrorTemplates(List.of(summary));

        assertThat(summary.getErrorTemplate()).isEqualTo("KeyError: 'owner' @ owner_id@/app/app.py:50");
    }

    @Test
    void shouldKeepTemplatesAlreadyReadFromAReport() {
        CatsTestCaseSummary summary = createTestCaseSummary(500, "Server Error", "error", "boom");
        summary.assignErrorTemplate("cafebabe", "persisted");

        ClusterCompute.assignErrorTemplates(List.of(summary));

        assertThat(summary.getErrorTemplateId()).isEqualTo("cafebabe");
    }

    @Test
    void shouldReadErrorBodiesByTheFieldRolesTheContractDeclares() {
        String contract = """
                openapi: 3.0.3
                info: {title: orders, version: "1"}
                paths:
                  /orders:
                    post:
                      responses:
                        '4XX':
                          description: client error
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  what: {type: string}
                                  trackingRef: {type: string, format: uuid}
                                  correlationId: {type: string}
                """;
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(contract, null, null).getOpenAPI();
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummaryAt("/orders", "{\"what\":\"Name too long\",\"trackingRef\":\"TR-A7F3\",\"correlationId\":\"c-91x\"}"),
                createTestCaseSummaryAt("/orders", "{\"what\":\"Name too long\",\"trackingRef\":\"TR-B0C4\",\"correlationId\":\"c-77q\"}"));

        ClusterCompute.assignErrorTemplates(testCases, openAPI);

        assertThat(testCases.getFirst().getErrorTemplate()).isEqualTo("Name too long");
        assertThat(testCases.getFirst().getErrorTemplateId()).isEqualTo(testCases.getLast().getErrorTemplateId());
    }

    @Test
    void shouldReadErrorBodiesByFieldNameWithoutContract() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummaryAt("/orders", "{\"what\":\"Name too long\",\"trackingRef\":\"TR-A7F3\",\"correlationId\":\"c-91x\"}"),
                createTestCaseSummaryAt("/orders", "{\"what\":\"Name too long\",\"trackingRef\":\"TR-B0C4\",\"correlationId\":\"c-77q\"}"));

        ClusterCompute.assignErrorTemplates(testCases);

        assertThat(testCases.getFirst().getErrorTemplateId()).isNotEqualTo(testCases.getLast().getErrorTemplateId());
    }

    private static List<Integer> templateGroupSizes(List<CatsTestCaseSummary> testCases) {
        ClusterCompute.assignErrorTemplates(testCases);
        return Optional.ofNullable(testCases).orElse(List.of()).stream().filter(Objects::nonNull)
                .filter(test -> test.getErrorTemplateId() != null)
                .collect(Collectors.groupingBy(test -> test.getResultReason() + "|" + test.getErrorTemplateId(), LinkedHashMap::new, Collectors.counting()))
                .values().stream().map(Long::intValue).sorted(Comparator.reverseOrder()).toList();
    }

    private static CatsTestCaseSummary createTestCaseSummaryAt(String contractPath, String responseBody) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(CatsRequest.builder().httpMethod("POST").build());
        testCase.setFuzzer("TestFuzzer");
        testCase.setResponse(CatsResponse.builder().responseCode(400).body(responseBody).build());
        testCase.setResult("error");
        testCase.setPath(contractPath);
        testCase.setContractPath(contractPath);
        testCase.setResultReason("Bad Request");
        testCase.setTestId(CatsRandom.instance().nextInt(2000) + "");
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }

    private static CatsTestCaseSummary createTestCaseSummary(int httpResponseCode, String resultReason, String result, String responseBody) {
        return createTestCaseSummary(httpResponseCode, resultReason, result, responseBody, null);
    }

    private static CatsTestCaseSummary createTestCaseSummary(int httpResponseCode, String resultReason, String result, String responseBody,
                                                             String requestPayload) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(CatsRequest.builder().httpMethod("POST").payload(requestPayload).build());
        testCase.setFuzzer("TestFuzzer");
        testCase.setResponse(CatsResponse.builder().responseCode(httpResponseCode).body(responseBody).build());
        testCase.setResult(result);
        testCase.setPath("/path/" + CatsRandom.instance().nextInt(1000));
        testCase.setContractPath(testCase.getPath());
        testCase.setResultReason(resultReason);
        testCase.setTestId(CatsRandom.instance().nextInt(2000) + "");
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }
}