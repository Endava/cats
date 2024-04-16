package com.endava.cats.io;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.KeyValuePair;
import com.endava.cats.report.TestCaseListener;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.net.Proxy;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
class ServiceCallerTest {

    public static WireMockServer wireMockServer;
    @Inject
    AuthArguments authArguments;
    @Inject
    ApiArguments apiArguments;
    @Inject
    ProcessingArguments processingArguments;
    @Inject
    CatsGlobalContext catsGlobalContext;
    FilesArguments filesArguments;
    private ServiceCaller serviceCaller;

    @BeforeAll
    public static void setup() {
        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.get("/not-json").willReturn(WireMock.ok("<html>test</html>")));
        wireMockServer.stubFor(WireMock.post("/pets").willReturn(WireMock.ok("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.put("/pets").willReturn(WireMock.aResponse().withBody("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.get("/pets/1").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
        wireMockServer.stubFor(WireMock.get("/pets/1?limit=2").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
        wireMockServer.stubFor(WireMock.get("/pets/999?id=1").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
        wireMockServer.stubFor(WireMock.get("/pets/fault/reset").willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        wireMockServer.stubFor(WireMock.get("/pets/fault/empty").willReturn(WireMock.aResponse().withFault(Fault.EMPTY_RESPONSE)));
        wireMockServer.stubFor(WireMock.get("/pets/fault/malformed").willReturn(WireMock.aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        wireMockServer.stubFor(WireMock.get("/pets/fault/random").willReturn(WireMock.aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        wireMockServer.stubFor(WireMock.delete("/pets/1").willReturn(WireMock.aResponse()));
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo("/pets/1")).willReturn(WireMock.aResponse()));
        wireMockServer.stubFor(WireMock.trace(WireMock.urlEqualTo("/pets/1")).willReturn(WireMock.aResponse()));
        wireMockServer.stubFor(WireMock.patch(WireMock.urlEqualTo("/pets")).willReturn(WireMock.aResponse()));
    }

    @AfterAll
    public static void clean() {
        wireMockServer.stop();
    }

    @BeforeEach
    public void setupEach() throws Exception {
        filesArguments = new FilesArguments();
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        serviceCaller = new ServiceCaller(catsGlobalContext, testCaseListener, filesArguments, authArguments, apiArguments, processingArguments);
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(authArguments, "basicAuth", "user:password");
        ReflectionTestUtils.setField(filesArguments, "refDataFile", new File("src/test/resources/refFields.yml"));
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("src/test/resources/headers.yml"));
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParamsEmpty.yml"));
        ReflectionTestUtils.setField(filesArguments, "params", List.of("gid:1", "test:2"));
        ReflectionTestUtils.setField(authArguments, "sslKeystore", null);
        ReflectionTestUtils.setField(authArguments, "proxyHost", null);
        ReflectionTestUtils.setField(authArguments, "proxyPort", 0);

        filesArguments.loadHeaders();
        filesArguments.loadRefData();
        filesArguments.loadURLParams();
        filesArguments.loadQueryParams();
        catsGlobalContext.getPostSuccessfulResponses().clear();
    }

    @Test
    void shouldSetRateLimiter() {
        ReflectionTestUtils.setField(apiArguments, "maxRequestsPerMinute", 30);
        serviceCaller.initRateLimiter();
        serviceCaller.initHttpClient();

        long t0 = System.currentTimeMillis();
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());
        long t1 = System.currentTimeMillis();

        Assertions.assertThat(t1 - t0).isGreaterThan(3900);
    }

    @Test
    void shouldNotSetRateLimiter() {
        serviceCaller.initRateLimiter();
        serviceCaller.initHttpClient();

        long t0 = System.currentTimeMillis();
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        long t1 = System.currentTimeMillis();

        Assertions.assertThat(t1 - t0).isLessThan(1000);
    }

    @Test
    void givenAServer_whenDoingADeleteCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.DELETE)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();
    }

    @Test
    void shouldSendUrlFormEncoded() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{\"id\":\"1\",\"test\":2}").httpMethod(HttpMethod.POST)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/x-www-form-urlencoded").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).contains("OK");
        wireMockServer.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/pets")).withRequestBody(WireMock.equalTo("test=2&id=1")));
    }

    @Test
    void shouldNotReturnJson() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/not-json").httpMethod(HttpMethod.GET)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).contains("html");
        Assertions.assertThat(catsResponse.getJsonBody().toString()).contains("notAJson");
    }

    @ParameterizedTest
    @CsvSource({"/pets/fault/reset,958,connection reset", "/pets/fault/malformed,957,protocol exception", "/pets/fault/random,952,empty reply from server", "/pets/fault/empty,952,empty reply from server"})
    void shouldHandleIOExceptions(String path, String responseCode, String expectedBody) {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath(path).httpMethod(HttpMethod.GET)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo(responseCode);
        Assertions.assertThat(catsResponse.getBody()).contains(expectedBody);
        Assertions.assertThat(catsResponse.getJsonBody().toString()).contains("notAJson");
    }

    @Test
    void shouldNotConvertToUrlFormEncodedWhenError() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'id':1}").httpMethod(HttpMethod.POST)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/x-www-form-urlencoded").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).contains("OK");
        wireMockServer.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/pets")).withRequestBody(WireMock.equalTo("{'id':1}")));
    }

    @Test
    void givenAServer_whenDoingAHeadCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPatchCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'id':'1'}").httpMethod(HttpMethod.PATCH)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();
    }

    @Test
    void givenAServer_whenDoingATraceCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.TRACE)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPostCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}").httpMethod(HttpMethod.POST)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAPostCallAndServerUnavailable_thenProperDetailsAreBeingReturned() {
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:111");

        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        ServiceData data = ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}").httpMethod(HttpMethod.POST)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build();
        CatsResponse response = serviceCaller.call(data);
        Assertions.assertThat(response.getResponseCode()).isEqualTo(953);
    }

    @Test
    void givenAServer_whenDoingAPutCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'field':'newValue'}").httpMethod(HttpMethod.PUT)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAGetCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1','limit':2,'no':null}").httpMethod(HttpMethod.GET)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build()))
                .queryParams(Set.of("limit", "no")).contentType("application/json").build());

        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/pets/1?limit=2")));
        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'pet':'pet'}");
    }

    @Test
    void shouldRemoveRefDataFieldsWhichAreMarkedForRemoval() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        ServiceData data = ServiceData.builder().relativePath("/pets").payload("{\"id\":\"1\", \"field\":\"old_value\",\"name\":\"cats\"}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).contentType("application/json").build();
        String newPayload = serviceCaller.replacePayloadWithRefData(data);
        Assertions.assertThat(newPayload).contains("newValue", "id", "field").doesNotContain("cats", "name");
    }

    @Test
    void shouldLoadKeystoreAndCreateSSLFactory() {
        ReflectionTestUtils.setField(authArguments, "sslKeystore", "src/test/resources/cats.jks");
        ReflectionTestUtils.setField(authArguments, "sslKeystorePwd", "password");
        ReflectionTestUtils.setField(authArguments, "sslKeyPwd", "password");

        serviceCaller.initHttpClient();
        Assertions.assertThat(serviceCaller.okHttpClient).isNotNull();
    }

    @Test
    void shouldNotCreateSSLFactoryWhenKeystoreInvalid() {
        ReflectionTestUtils.setField(authArguments, "sslKeystore", "src/test/resources/cats_bad.jks");

        serviceCaller.initHttpClient();
        Assertions.assertThat(serviceCaller.okHttpClient).isNull();
    }

    @Test
    void shouldSetProxy() {
        ReflectionTestUtils.setField(authArguments, "proxyHost", "http://localhost");
        ReflectionTestUtils.setField(authArguments, "proxyPort", 8080);

        serviceCaller.initHttpClient();
        Assertions.assertThat(serviceCaller.okHttpClient).isNotNull();
        Assertions.assertThat(serviceCaller.okHttpClient.proxy()).isNotEqualTo(Proxy.NO_PROXY);
    }

    @Test
    void shouldNotCreateSSLFactoryWhenKeystoreEmpty() {
        serviceCaller.initHttpClient();
        Assertions.assertThat(serviceCaller.okHttpClient).isNotNull();
    }

    @Test
    void shouldGetDefaultTimeouts() {
        Assertions.assertThat(apiArguments.getConnectionTimeout()).isEqualTo(10);
        Assertions.assertThat(apiArguments.getReadTimeout()).isEqualTo(10);
        Assertions.assertThat(apiArguments.getWriteTimeout()).isEqualTo(10);
        serviceCaller.initHttpClient();

        Assertions.assertThat(serviceCaller.okHttpClient.readTimeoutMillis()).isEqualTo(10000);
        Assertions.assertThat(serviceCaller.okHttpClient.connectTimeoutMillis()).isEqualTo(10000);
        Assertions.assertThat(serviceCaller.okHttpClient.writeTimeoutMillis()).isEqualTo(10000);
    }

    @Test
    void shouldChangeTimeouts() {
        ReflectionTestUtils.setField(apiArguments, "connectionTimeout", 50);
        ReflectionTestUtils.setField(apiArguments, "readTimeout", 49);
        ReflectionTestUtils.setField(apiArguments, "writeTimeout", 48);
        serviceCaller.initHttpClient();

        Assertions.assertThat(serviceCaller.okHttpClient.readTimeoutMillis()).isEqualTo(49000);
        Assertions.assertThat(serviceCaller.okHttpClient.connectTimeoutMillis()).isEqualTo(50000);
        Assertions.assertThat(serviceCaller.okHttpClient.writeTimeoutMillis()).isEqualTo(48000);
    }

    @Test
    void shouldRemoveSkippedHeaders() {
        ServiceData data = ServiceData.builder().headers(Set.of(CatsHeader.builder().name("catsHeader").build())).skippedHeaders(Set.of("catsHeader")).build();
        List<KeyValuePair<String, Object>> headers = serviceCaller.buildHeaders(data);
        Optional<KeyValuePair<String, Object>> catsHeader = headers.stream().filter(header -> header.getKey().equalsIgnoreCase("catsHeader")).findFirst();

        Assertions.assertThat(catsHeader).isEmpty();
    }

    @Test
    void shouldAddAdditionalQueryParams() throws Exception {
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParams.yml"));
        filesArguments.loadQueryParams();

        String finalUrl = serviceCaller.addAdditionalQueryParams("http://localhost", "/pets");
        Assertions.assertThat(finalUrl).contains("jwt", "small", "large", "param");
    }

    @Test
    void shouldMergeFuzzingForSuppliedHeaders() {
        ServiceData data = ServiceData.builder().headers(Set.of(CatsHeader.builder().name("catsFuzzedHeader").value("  anotherValue").build()))
                .fuzzedHeader("catsFuzzedHeader").contentType("application/json").build();
        List<KeyValuePair<String, Object>> headers = serviceCaller.buildHeaders(data);
        List<KeyValuePair<String, Object>> catsHeader = headers.stream().filter(header -> header.getKey().equalsIgnoreCase("catsFuzzedHeader")).collect(Collectors.toList());

        Assertions.assertThat(catsHeader).hasSize(1);
        Assertions.assertThat(catsHeader.get(0).getValue()).isEqualTo("  cats");
    }

    @Test
    void shouldAddHeaderWhenAddUserHeadersOffButSuppliedInHeadersFile() {
        ServiceData data = ServiceData.builder()
                .headers(Set.of(CatsHeader.builder().name("simpleHeader").value("simpleValue").build(), CatsHeader.builder().name("catsFuzzedHeader").value("anotherValue").build()))
                .fuzzedHeader("catsFuzzedHeader").addUserHeaders(false).contentType("application/json").build();

        List<KeyValuePair<String, Object>> headers = serviceCaller.buildHeaders(data);
        List<String> headerNames = headers.stream().map(KeyValuePair::getKey).collect(Collectors.toList());
        Assertions.assertThat(headerNames).doesNotContain("header").contains("catsFuzzedHeader", "simpleHeader");

        List<KeyValuePair<String, Object>> catsHeader = headers.stream().filter(header -> header.getKey().equalsIgnoreCase("catsFuzzedHeader")).collect(Collectors.toList());
        Assertions.assertThat(catsHeader).hasSize(1);
        Assertions.assertThat(catsHeader.get(0).getValue()).isEqualTo("cats");
    }

    @Test
    void shouldAddHeaderWhenAddUserHeadersOffButAuthenticationHeader() {
        ServiceData data = ServiceData.builder()
                .headers(Set.of(CatsHeader.builder().name("simpleHeader").value("simpleValue").build()))
                .relativePath("auth-header").contractPath("auth-header")
                .fuzzedHeader("catsFuzzedHeader").addUserHeaders(false).contentType("application/json").build();

        List<KeyValuePair<String, Object>> headers = serviceCaller.buildHeaders(data);
        List<String> headerNames = headers.stream().map(KeyValuePair::getKey).collect(Collectors.toList());
        Assertions.assertThat(headerNames).doesNotContain("header", "catsFuzzedHeader").contains("simpleHeader", "jwt");
    }

    @Test
    void shouldPreserverNumberOfHeadersWhenHeaderSuppliedAndPresentInOpenApi() {
        ServiceData data = ServiceData.builder()
                .headers(List.of(CatsHeader.builder().name("catsFuzzedHeader").value("simpleValue").build(),
                        CatsHeader.builder().name("catsFuzzedHeader").value("anotherValue").build()))
                .relativePath("auth-header")
                .fuzzedHeader("anotherOne").contentType("application/json").build();

        List<KeyValuePair<String, Object>> headers = serviceCaller.buildHeaders(data);
        long numberOfHeaders = headers.stream().filter(header -> header.getKey().equalsIgnoreCase("catsFuzzedHeader")).count();
        Set<Object> headerValues = headers.stream().filter(header -> header.getKey().equalsIgnoreCase("catsFuzzedHeader")).map(KeyValuePair::getValue).collect(Collectors.toSet());

        Assertions.assertThat(numberOfHeaders).isEqualTo(2);
        Assertions.assertThat(headerValues).hasSize(1).containsExactly("cats");
    }

    @Test
    void shouldReturnEmptyMapWhenNoPostStored() {
        ServiceData data = ServiceData.builder().relativePath("/test/{testId}").httpMethod(HttpMethod.DELETE).build();

        Map<String, String> cachedPost = serviceCaller.getPathParamFromCorrespondingPostIfDelete(data);
        Assertions.assertThat(cachedPost).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapWhenNotDelete() {
        ServiceData data = ServiceData.builder().relativePath("/test/{testId}").httpMethod(HttpMethod.GET).build();

        Map<String, String> cachedPost = serviceCaller.getPathParamFromCorrespondingPostIfDelete(data);
        Assertions.assertThat(cachedPost).isEmpty();
    }


    @Test
    void shouldReturnEmptyWhenPostStoredButNotMatchingElement() {
        ServiceData data = ServiceData.builder().relativePath("/test/{testId}").httpMethod(HttpMethod.DELETE).build();
        Deque<String> existingPost = new ArrayDeque<>();
        existingPost.add("{\"field\": 23}");
        catsGlobalContext.getPostSuccessfulResponses().put("/test", existingPost);

        Map<String, String> cachedPost = serviceCaller.getPathParamFromCorrespondingPostIfDelete(data);
        Assertions.assertThat(cachedPost).isEmpty();
    }

    @Test
    void shouldReturnPostParamWhenMatching() {
        ServiceData data = ServiceData.builder().relativePath("/test/{testId}").httpMethod(HttpMethod.DELETE).build();
        Deque<String> existingPost = new ArrayDeque<>();
        existingPost.add("{\"testId\": 23}");
        catsGlobalContext.getPostSuccessfulResponses().put("/test", existingPost);

        Map<String, String> cachedPost = serviceCaller.getPathParamFromCorrespondingPostIfDelete(data);
        Assertions.assertThat(cachedPost).containsEntry("testId", "23");
    }

    @ParameterizedTest
    @CsvSource({"999,true,/pets/999?id=1", "1,false,/pets/1"})
    void shouldReplaceUrlParams(String id, boolean replaceUrlParams, String expectedUrl) {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("id:" + id, "test:2"));
        filesArguments.loadURLParams();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .httpMethod(HttpMethod.GET).headers(Collections.singleton(CatsHeader.builder().name("header").value("header")
                        .build())).contentType("application/json").replaceUrlParams(replaceUrlParams).build());

        Assertions.assertThat(catsResponse).isNotNull();
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(expectedUrl)));
    }

    @Test
    void shouldReplacePathVariables() {
        String json = """
                {
                    "configId": "123",
                    "tenantId": "abcd"
                }
                """;
        String url = "http://localhost:8080/configs/{configId}/tenants/{tenantId}";
        String result = serviceCaller.addPathParamsIfNotReplaced(url, json);
        Assertions.assertThat(result).isEqualTo("http://localhost:8080/configs/123/tenants/abcd");
    }
}
