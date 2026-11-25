package com.endava.cats.fuzzer.special;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.auth.session.SessionHealthMonitor;
import com.endava.cats.auth.wfc.WfcAuthProvider;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.NoMediaType;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
class MultipartFunctionalFlowTest {
    private static final String CONTRACT_PATH = "/cases/{caseId}/evidence/uploads";
    private static final String URL_PATH = "/cases/case-1/evidence/uploads";
    private static final String RESOURCES = "src/test/resources/multipart/";
    private static final String IDEMPOTENCY_HEADER = "X-XE-IdempotencyKey";
    private static final String CORRELATION_HEADER = "X-XE-CorrelationId";
    private static final String MISMATCH_REASON = "Verify parameters not matching response";
    private static final String SCENARIO = "idempotency";
    private static final Path FIXTURE = Path.of(RESOURCES + "all-bytes.bin");
    private static WireMockServer wireMockServer;

    @Inject
    AuthArguments authArguments;
    @Inject
    ApiArguments apiArguments;
    @Inject
    ProcessingArguments processingArguments;
    @Inject
    ReportingArguments reportingArguments;
    @Inject
    FilterArguments filterArguments;
    @Inject
    ValidDataFormat validDataFormat;
    @Inject
    CatsGlobalContext catsGlobalContext;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private FunctionalFlowFuzzer functionalFlowFuzzer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        wireMockServer.resetAll();
        filesArguments = new FilesArguments();
        WfcAuthProvider wfcAuthProvider = new WfcAuthProvider(authArguments, apiArguments);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        ServiceCaller serviceCaller = new ServiceCaller(testCaseListener, filesArguments, authArguments, apiArguments, processingArguments,
                reportingArguments, wfcAuthProvider, new RuntimeResourcePool(processingArguments, filesArguments),
                filterArguments, new SessionHealthMonitor(wfcAuthProvider));
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(authArguments, "basicAuth", null);
        ReflectionTestUtils.setField(authArguments, "wfcAuthFile", null);
        ReflectionTestUtils.setField(authArguments, "wfcAuthName", null);
        ReflectionTestUtils.setField(authArguments, "sslKeystore", null);
        ReflectionTestUtils.setField(authArguments, "sslKeystorePwd", null);
        ReflectionTestUtils.setField(authArguments, "sslKeyPwd", null);
        ReflectionTestUtils.setField(authArguments, "insecure", false);
        ReflectionTestUtils.setField(authArguments, "proxyHost", null);
        ReflectionTestUtils.setField(authArguments, "proxyPort", 0);
        ReflectionTestUtils.setField(processingArguments, "reuseSuccessfulResources", false);
        ReflectionTestUtils.setField(apiArguments, "connectionTimeout", 10);
        ReflectionTestUtils.setField(apiArguments, "readTimeout", 10);
        ReflectionTestUtils.setField(apiArguments, "writeTimeout", 10);
        ReflectionTestUtils.setField(apiArguments, "callTimeout", 20);
        ReflectionTestUtils.setField(apiArguments, "maxResponseBytes", 10485760L);
        ReflectionTestUtils.setField(apiArguments, "maxRequestsPerMinute", 10000);
        ReflectionTestUtils.setField(apiArguments, "maxRateLimitRetries", 3);
        ReflectionTestUtils.setField(apiArguments, "maxRetryAfterInSec", 30);
        ReflectionTestUtils.setField(reportingArguments, "showSecrets", false);
        ReflectionTestUtils.setField(reportingArguments, "maskHeaders", null);
        reportingArguments.resetSensitiveData();
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();
        functionalFlowFuzzer = new FunctionalFlowFuzzer(filesArguments, serviceCaller, testCaseListener);
    }

    @Test
    void shouldReplaySameIdempotencyKeyAcrossFlowSteps() throws Exception {
        byte[] fixture = Files.readAllBytes(FIXTURE);
        stubOutcomes("APPLIED", "REPEATED");

        runFlow("flow-idempotency-variable.yml", "custom-headers-file-correlation-only.yml");

        List<LoggedRequest> requests = requests();
        List<UUID> idempotencyKeys = uuids(requests, IDEMPOTENCY_HEADER);
        Assertions.assertThat(idempotencyKeys).hasSize(2).containsOnly(idempotencyKeys.getFirst());
        Assertions.assertThat(uuids(requests, CORRELATION_HEADER)).hasSize(2).doesNotHaveDuplicates();
        Assertions.assertThat(requests).extracting(request -> request.getHeader("Content-Type"))
                .allSatisfy(contentType -> Assertions.assertThat(contentType).startsWith("multipart/form-data; boundary="));
        Assertions.assertThat(withoutBoundary(requests.get(0))).isEqualTo(withoutBoundary(requests.get(1)));
        Assertions.assertThat(indexOf(requests.get(0).getBody(), fixture)).isNotNegative();
        Assertions.assertThat(indexOf(requests.get(1).getBody(), fixture)).isNotNegative();
        verifyStepSuccesses(2);
        Mockito.verify(testCaseListener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(FuzzingData.class),
                Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
    }

    @Test
    void shouldReplaceStepHeaderWithHeadersFileValue() throws Exception {
        stubOutcomes("APPLIED", "APPLIED");

        runFlow("flow-idempotency-variable.yml", "custom-headers-file.yml");

        List<LoggedRequest> requests = requests();
        Assertions.assertThat(uuids(requests, IDEMPOTENCY_HEADER)).hasSize(2).doesNotHaveDuplicates();
        Assertions.assertThat(uuids(requests, CORRELATION_HEADER)).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void shouldFailFlowStepWhenFirstOutcomeIsNotApplied() throws Exception {
        stubOutcomes("REPEATED", "REPEATED");

        runFlow("flow-idempotency-variable.yml", "custom-headers-file-correlation-only.yml");

        requests();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = Mockito.inOrder(testCaseListener);
        inOrder.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.any(FuzzingData.class),
                Mockito.eq(MISMATCH_REASON), messageCaptor.capture(), Mockito.any(Object[].class));
        inOrder.verify(testCaseListener).reportResult(Mockito.any(), Mockito.any(FuzzingData.class), Mockito.any(CatsResponse.class),
                Mockito.any(ResponseCodeFamily.class), Mockito.eq(true), Mockito.eq(true));
        Assertions.assertThat(messageCaptor.getValue()).contains("[outcome] with value [REPEATED] not matching [APPLIED]");
    }

    @Test
    void shouldFailFlowStepWhenReplayOutcomeIsNotRepeated() throws Exception {
        stubOutcomes("APPLIED", "APPLIED");

        runFlow("flow-idempotency-variable.yml", "custom-headers-file-correlation-only.yml");

        requests();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = Mockito.inOrder(testCaseListener);
        inOrder.verify(testCaseListener).reportResult(Mockito.any(), Mockito.any(FuzzingData.class), Mockito.any(CatsResponse.class),
                Mockito.any(ResponseCodeFamily.class), Mockito.eq(true), Mockito.eq(true));
        inOrder.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.any(FuzzingData.class),
                Mockito.eq(MISMATCH_REASON), messageCaptor.capture(), Mockito.any(Object[].class));
        Assertions.assertThat(messageCaptor.getValue()).contains("[outcome] with value [APPLIED] not matching [REPEATED]");
    }

    @Test
    void shouldGenerateStepHeaderPerFlowStep() throws Exception {
        stubOutcomes("APPLIED", "APPLIED");

        runFlow("flow-idempotency-per-step.yml", null);

        Assertions.assertThat(uuids(requests(), IDEMPOTENCY_HEADER)).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void shouldOmitPartAndAddExtraPartInFlowStepsInOrder() throws Exception {
        wireMockServer.stubFor(WireMock.post(URL_PATH).willReturn(WireMock.okJson("{}")));
        functionalFlowFuzzer.fuzz(multipartOperation());

        functionalFlowFuzzer.executeDocument(FunctionalFlowParser.parseContent("""
                steps:
                  - name: Remove checksum part
                    POST: /cases/case-1/evidence/uploads
                    omit:
                      - checksum
                    body:
                      file: "cats_file:src/test/resources/multipart/all-bytes.bin"
                      evidenceType: OTHER
                    status: 200
                  - name: Add extra packageId part
                    POST: /cases/case-1/evidence/uploads
                    body:
                      file: "cats_file:src/test/resources/multipart/all-bytes.bin"
                      evidenceType: PROOF_OF_DELIVERY
                      packageId: x
                    status: 200
                """), "multipart-parts-flow", functionalFlowFuzzer);

        List<LoggedRequest> received = requests();
        String firstBody = new String(received.get(0).getBody(), StandardCharsets.ISO_8859_1);
        Assertions.assertThat(firstBody)
                .doesNotContain("name=\"checksum\"")
                .contains("name=\"file\"")
                .contains("name=\"evidenceType\"")
                .contains("\r\n\r\nOTHER\r\n");

        String secondBody = new String(received.get(1).getBody(), StandardCharsets.ISO_8859_1);
        Assertions.assertThat(secondBody)
                .contains("name=\"checksum\"")
                .contains("name=\"packageId\"")
                .contains("\r\n\r\nx\r\n")
                .contains("\r\n\r\nPROOF_OF_DELIVERY\r\n");
    }

    private void verifyStepSuccesses(int times) {
        Mockito.verify(testCaseListener, Mockito.times(times)).reportResult(Mockito.any(), Mockito.any(FuzzingData.class),
                Mockito.any(CatsResponse.class), Mockito.any(ResponseCodeFamily.class), Mockito.eq(true), Mockito.eq(true));
    }

    private void stubOutcomes(String first, String second) {
        wireMockServer.stubFor(WireMock.post(URL_PATH).inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.okJson("{\"outcome\":\"" + first + "\"}")).willSetStateTo("replayed"));
        wireMockServer.stubFor(WireMock.post(URL_PATH).inScenario(SCENARIO).whenScenarioStateIs("replayed")
                .willReturn(WireMock.okJson("{\"outcome\":\"" + second + "\"}")));
    }

    private void runFlow(String flowFile, String headersFile) throws Exception {
        filesArguments.setFunctionalFlowFile(new File(RESOURCES + flowFile));
        ReflectionTestUtils.setField(filesArguments, "headersFile", headersFile == null ? null : new File(RESOURCES + headersFile));
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParamsEmpty.yml"));
        filesArguments.loadHeaders();
        filesArguments.loadQueryParams();
        filesArguments.loadURLParams();
        functionalFlowFuzzer.fuzz(multipartOperation());
        functionalFlowFuzzer.executeFlows();
    }

    private FuzzingData multipartOperation() throws Exception {
        ProcessingArguments mockedProcessing = Mockito.mock(ProcessingArguments.class);
        Mockito.when(mockedProcessing.getUseExamples()).thenReturn(true);
        Mockito.when(mockedProcessing.getLimitXxxOfCombinations()).thenReturn(10);
        Mockito.when(mockedProcessing.getContentType()).thenReturn(List.of(JsonUtils.JSON_WILDCARD, "application/x-www-form-urlencoded"));
        Mockito.when(mockedProcessing.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(true, true, false, true));
        Mockito.when(mockedProcessing.getSelfReferenceDepth()).thenReturn(4);
        FuzzingDataFactory factory = new FuzzingDataFactory(filesArguments, mockedProcessing, catsGlobalContext,
                validDataFormat, Mockito.mock(FilterArguments.class));
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = new OpenAPIParser().readContents(
                Files.readString(Paths.get("src/test/resources/multipart-upload.yml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application\\/.*\\+?json"));
        catsGlobalContext.getSchemaMap().clear();
        catsGlobalContext.getSchemaMap().putAll(schemas);
        catsGlobalContext.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        catsGlobalContext.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        catsGlobalContext.getSchemaMap().remove("");
        catsGlobalContext.setOpenAPI(openAPI);
        return factory.fromPathItem(CONTRACT_PATH, openAPI.getPaths().get(CONTRACT_PATH), openAPI).stream()
                .filter(data -> data.getMethod() == HttpMethod.POST).findFirst().orElseThrow();
    }

    private static List<LoggedRequest> requests() {
        List<LoggedRequest> requests = wireMockServer.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(URL_PATH)));
        Assertions.assertThat(requests).hasSize(2);
        return requests;
    }

    private static List<UUID> uuids(List<LoggedRequest> requests, String header) {
        return requests.stream().map(request -> UUID.fromString(request.getHeader(header))).toList();
    }

    private static String withoutBoundary(LoggedRequest request) {
        String contentType = request.getHeader("Content-Type");
        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
        return new String(request.getBody(), StandardCharsets.ISO_8859_1).replace(boundary, "<boundary>");
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int i = 0; i <= source.length - target.length; i++) {
            if (Arrays.equals(source, i, i + target.length, target, 0, target.length)) {
                return i;
            }
        }
        return -1;
    }
}
