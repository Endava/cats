package com.endava.cats.report.wfc;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.webfuzzing.commons.faults.DefinedFaultCategory;
import com.webfuzzing.commons.report.FaultCategoryId;
import com.webfuzzing.commons.report.Report;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@QuarkusTest
class WfcReportBuilderTest {

    @Test
    void shouldEmitAndDeduplicate202And203ForSpecWithoutSecuritySchemes() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: No Auth API
                  version: 1.0.0
                paths:
                  /items:
                    get:
                      responses:
                        '200':
                          description: ok
                        default:
                          description: fallback
                """;
        OpenAPI openAPI = parseOpenApi(yaml);

        CatsTestCaseSummary first401 = summary("Test 1", "GET", "/items", 401);
        CatsTestCaseSummary duplicate401 = summary("Test 2", "GET", "/items", 401);
        CatsTestCaseSummary forbiddenWithout401 = summary("Test 3", "GET", "/items", 403);

        Report report = WfcReportBuilder.build(
                List.of(first401, duplicate401, forbiddenWithout401),
                null, openAPI, List.of(), null, "1.0.0", 1L);

        Assertions.assertThat(report.getFaults().getFoundFaults()).hasSize(3);
        Assertions.assertThat(report.getFaults().getTotalNumber()).isEqualTo(2);
        Assertions.assertThat(report.getFaults().getFoundFaults())
                .flatMap(ff -> ff.getFaultCategories())
                .extracting(FaultCategoryId::getCode, FaultCategoryId::getContext)
                .containsOnly(
                        Assertions.tuple(DefinedFaultCategory.SCHEMA_STATUS_NO_401_IF_NO_AUTH.getCode(), null),
                        Assertions.tuple(DefinedFaultCategory.SCHEMA_STATUS_NO_403_IF_NO_401.getCode(), null));
    }

    @Test
    void shouldHandleExplicitlyPublicOperationAndRefResponsesAnd4xxRange() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Mixed Auth API
                  version: 1.0.0
                security:
                  - bearerAuth: []
                paths:
                  /public:
                    get:
                      security: []
                      responses:
                        '200':
                          description: ok
                  /ref-401:
                    get:
                      responses:
                        '200':
                          description: ok
                        '401':
                          $ref: '#/components/responses/UnauthorizedErr'
                  /range-4xx:
                    get:
                      responses:
                        '200':
                          description: ok
                        '4XX':
                          description: client error range
                components:
                  securitySchemes:
                    bearerAuth:
                      type: http
                      scheme: bearer
                  responses:
                    UnauthorizedErr:
                      description: unauthorized
                """;
        OpenAPI openAPI = parseOpenApi(yaml);

        CatsTestCaseSummary public401First = summary("Test 1", "GET", "/public", 401);
        CatsTestCaseSummary public401Second = summary("Test 2", "GET", "/public", 401);
        CatsTestCaseSummary secured401 = summary("Test 3", "GET", "/ref-401", 401);
        CatsTestCaseSummary ref403With401Declared = summary("Test 4", "GET", "/ref-401", 403);
        CatsTestCaseSummary range403With4xxDeclared = summary("Test 5", "GET", "/range-4xx", 403);
        CatsTestCaseSummary undocumentedPath401 = summary("Test 6", "GET", "/undocumented", 401);

        Report report = WfcReportBuilder.build(
                List.of(public401First, public401Second, secured401, ref403With401Declared, range403With4xxDeclared, undocumentedPath401),
                null, openAPI, List.of(), null, "1.0.0", 2L);

        Assertions.assertThat(report.getFaults().getFoundFaults()).hasSize(2);
        Assertions.assertThat(report.getFaults().getTotalNumber()).isEqualTo(1);
        Assertions.assertThat(report.getFaults().getFoundFaults())
                .flatMap(ff -> ff.getFaultCategories())
                .extracting(FaultCategoryId::getCode, FaultCategoryId::getContext)
                .containsOnly(Assertions.tuple(
                        DefinedFaultCategory.SCHEMA_STATUS_NO_401_IF_NO_AUTH.getCode(), "security: []"));
    }

    @Test
    void shouldHandleNullAndIncompleteOpenApiInOperationAuthContractIndex() {
        Assertions.assertThat(OperationAuthContractIndex.from(null)).isEmpty();
        Assertions.assertThat(OperationAuthContractIndex.from(new OpenAPI())).isEmpty();

        Paths paths = new Paths();
        paths.addPathItem("/empty", new PathItem());
        paths.addPathItem("/no-responses", new PathItem().get(new Operation()));
        paths.addPathItem("/with-401", new PathItem().post(new Operation()
                .responses(new ApiResponses().addApiResponse("401", new ApiResponse()))));
        OpenAPI openAPI = new OpenAPI().paths(paths);

        Map<String, OperationAuthContract> index = OperationAuthContractIndex.from(openAPI);
        Assertions.assertThat(index)
                .containsEntry("GET:/no-responses", new OperationAuthContract(false, false, false))
                .containsEntry("POST:/with-401", new OperationAuthContract(false, false, true));
    }

    @Test
    void shouldKeepEndpointIdsAnd500FaultsWithinDeclaredOperationsWhileAttributingMethodProbesToDocumentedOperation() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Method Probe API
                  version: 1.0.0
                paths:
                  /items:
                    get:
                      responses:
                        '200':
                          description: ok
                    post:
                      responses:
                        '201':
                          description: created
                """;
        OpenAPI openAPI = parseOpenApi(yaml);

        CatsTestCase documented500 = buildTestCase("Test 1", "GET", "/items", 500, "error",
                Reason.UNEXPECTED_RESPONSE_CODE.value(), "HappyPath");
        CatsTestCase undeclaredMethod500 = buildTestCase("Test 2", "PROPFIND", "/items", 500, "error",
                Reason.UNEXPECTED_RESPONSE_CODE.value(), "NonRestHttpMethods");
        CatsTestCase undeclaredMethod200 = buildTestCase("Test 3", "DELETE", "/items", 200, "error",
                Reason.UNEXPECTED_RESPONSE_CODE.value(), "HttpMethods");
        CatsTestCase undeclaredMethod405 = buildTestCase("Test 4", "PUT", "/items", 405, "warn",
                Reason.MISSING_ALLOW_HEADER.value(), "HttpMethods");
        CatsTestCase dummyPath500 = buildTestCase("Test 5", "GET", "/cats-dummy-request", 500, "error",
                Reason.UNEXPECTED_RESPONSE_CODE.value(), "DummyRequest");

        Report report = WfcReportBuilder.build(
                List.of(
                        CatsTestCaseSummary.fromCatsTestCase(documented500),
                        CatsTestCaseSummary.fromCatsTestCase(undeclaredMethod500),
                        CatsTestCaseSummary.fromCatsTestCase(undeclaredMethod200),
                        CatsTestCaseSummary.fromCatsTestCase(undeclaredMethod405),
                        CatsTestCaseSummary.fromCatsTestCase(dummyPath500)),
                null, openAPI, List.of(), null, "1.0.0", 2L);

        Assertions.assertThat(report.getProblemDetails().getRest().getEndpointIds())
                .containsExactly("GET:/items", "POST:/items");
        Assertions.assertThat(report.getProblemDetails().getRest().getCoveredHttpStatus())
                .extracting(ce -> ce.getEndpointId())
                .containsOnly("GET:/items");
        Assertions.assertThat(report.getFaults().getFoundFaults())
                .allSatisfy(ff -> Assertions.assertThat(report.getProblemDetails().getRest().getEndpointIds())
                        .contains(ff.getOperationId()));
        Assertions.assertThat(report.getFaults().getFoundFaults())
                .flatMap(ff -> ff.getFaultCategories())
                .extracting(FaultCategoryId::getCode, FaultCategoryId::getContext)
                .containsExactlyInAnyOrder(
                        Assertions.tuple(DefinedFaultCategory.HTTP_STATUS_500.getCode(), "{}"),
                        Assertions.tuple(DefinedFaultCategory.SECURITY_HIDDEN_ACCESSIBLE_ENDPOINT.getCode(), "DELETE"),
                        Assertions.tuple(DefinedFaultCategory.HTTP_STATUS_NO_405_IF_NO_ALLOW.getCode(), "HttpMethods"));
    }

    private static CatsTestCase buildTestCase(String testId, String method, String path, int statusCode,
                                              String result, String reason, String fuzzer) {
        CatsTestCase tc = new CatsTestCase();
        tc.setTestId(testId);
        tc.setResult(result);
        tc.setResultReason(reason);
        tc.setFuzzer(fuzzer);
        tc.setPath(path);
        tc.setContractPath(path);
        tc.setRequest(CatsRequest.builder().httpMethod(method).payload("{}").build());
        tc.setResponse(CatsResponse.builder().responseCode(statusCode).body("{}").build());
        return tc;
    }

    private static OpenAPI parseOpenApi(String yaml) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        return new OpenAPIV3Parser().readContents(yaml, null, options).getOpenAPI();
    }

    private static CatsTestCaseSummary summary(String testId, String method, String path, int statusCode) {
        CatsTestCase tc = new CatsTestCase();
        tc.setTestId(testId);
        tc.setResult("warning");
        tc.setResultReason(Reason.UNDOCUMENTED_RESPONSE_CODE.value());
        tc.setFuzzer("HappyPath");
        tc.setPath(path);
        tc.setContractPath(path);
        tc.setRequest(CatsRequest.builder().httpMethod(method).payload("{}").build());
        tc.setResponse(CatsResponse.builder().responseCode(statusCode).body("{}").build());
        return CatsTestCaseSummary.fromCatsTestCase(tc);
    }
}
