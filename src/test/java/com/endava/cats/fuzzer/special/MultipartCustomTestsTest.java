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
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
class MultipartCustomTestsTest {
    private static final String CONTRACT_PATH = "/cases/{caseId}/evidence/uploads";
    private static final String URL_PATH = "/cases/case-1/evidence/uploads";
    private static final String RESOURCES = "src/test/resources/multipart/";
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
    private FunctionalFuzzer functionalFuzzer;
    private ServiceCaller serviceCaller;

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
    void setup() throws Exception {
        CatsRandom.initRandom(0);
        wireMockServer.resetAll();
        wireMockServer.stubFor(WireMock.post(URL_PATH).willReturn(WireMock.ok("{}")));
        filesArguments = new FilesArguments();
        WfcAuthProvider wfcAuthProvider = new WfcAuthProvider(authArguments, apiArguments);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        serviceCaller = new ServiceCaller(testCaseListener, filesArguments, authArguments, apiArguments, processingArguments,
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
        functionalFuzzer = new FunctionalFuzzer(filesArguments, new CustomFuzzerUtil(serviceCaller, testCaseListener),
                testCaseListener);
    }

    @Test
    void shouldSendFileFromCatsFileReferenceInCustomTest() throws Exception {
        byte[] fixture = Files.readAllBytes(FIXTURE);
        ArgumentCaptor<ResponseCodeFamily> family = ArgumentCaptor.forClass(ResponseCodeFamily.class);
        runCustomTests("custom-file.yml", null);

        LoggedRequest received = onlyRequest();
        Assertions.assertThat(received.getHeader("Content-Type")).startsWith("multipart/form-data; boundary=");
        String body = new String(received.getBody(), StandardCharsets.ISO_8859_1);
        Assertions.assertThat(body).contains("name=\"file\"; filename=\"all-bytes.bin\"")
                .contains("name=\"evidenceType\"").contains("name=\"checksum\"")
                .contains("\r\n\r\nOTHER\r\n").contains("\r\n\r\n" + sha256(fixture) + "\r\n");
        Assertions.assertThat(indexOf(received.getBody(), fixture)).isNotNegative();
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.any(FuzzingData.class), Mockito.any(CatsResponse.class),
                family.capture());
        Assertions.assertThat(family.getValue().allowedResponseCodes()).contains("200");
    }

    @Test
    void shouldRemovePartWithCatsRemoveField() throws Exception {
        runCustomTests("custom-remove-part.yml", null);

        String body = new String(onlyRequest().getBody(), StandardCharsets.ISO_8859_1);
        Assertions.assertThat(body).doesNotContain("name=\"checksum\"").contains("name=\"file\"").contains("name=\"evidenceType\"");
    }

    @Test
    void shouldAddExtraPartWithAdditionalProperties() throws Exception {
        runCustomTests("custom-additional-part.yml", null);

        String body = new String(onlyRequest().getBody(), StandardCharsets.ISO_8859_1);
        Assertions.assertThat(body).contains("name=\"packageId\"").contains("\r\n\r\nx\r\n");
    }

    @Test
    void shouldEvaluateUuidHeadersFromHeadersFilePerRequest() throws Exception {
        runCustomTests("custom-order.yml", "custom-headers-file.yml");

        List<LoggedRequest> requests = requests(3);
        Assertions.assertThat(uuids(requests, "X-XE-CorrelationId")).hasSize(3).doesNotHaveDuplicates();
        Assertions.assertThat(uuids(requests, "X-XE-IdempotencyKey")).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void shouldExecuteEntriesOfAPathInFileOrder() throws Exception {
        runCustomTests("custom-order.yml", null);

        List<String> evidenceTypes = requests(3).stream().map(request -> {
            String body = new String(request.getBody(), StandardCharsets.ISO_8859_1);
            int start = body.indexOf("name=\"evidenceType\"");
            return body.substring(body.indexOf("\r\n\r\n", start) + 4, body.indexOf("\r\n--", start));
        }).toList();
        Assertions.assertThat(evidenceTypes).containsExactly("B", "A", "C");
    }

    private void runCustomTests(String customFile, String headersFile) throws Exception {
        ReflectionTestUtils.setField(filesArguments, "customFuzzerFile", new File(RESOURCES + customFile));
        ReflectionTestUtils.setField(filesArguments, "headersFile", headersFile == null ? null : new File(RESOURCES + headersFile));
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParamsEmpty.yml"));
        filesArguments.loadCustomFuzzerFile();
        filesArguments.loadHeaders();
        filesArguments.loadQueryParams();
        filesArguments.loadURLParams();
        functionalFuzzer.fuzz(multipartOperation());
        functionalFuzzer.executeCustomFuzzerTests();
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

    private static LoggedRequest onlyRequest() {
        return requests(1).getFirst();
    }

    private static List<LoggedRequest> requests(int expected) {
        List<LoggedRequest> requests = wireMockServer.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(URL_PATH)));
        Assertions.assertThat(requests).hasSize(expected);
        return requests;
    }

    private static List<UUID> uuids(List<LoggedRequest> requests, String header) {
        return requests.stream().map(request -> UUID.fromString(request.getHeader(header))).toList();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
