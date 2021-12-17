package com.endava.cats.io;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;
import java.io.File;
import java.net.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
class ServiceCallerTest {

    public static WireMockServer wireMockServer;
    @Inject
    CatsDSLParser catsDSLParser;
    @Inject
    AuthArguments authArguments;
    @Inject
    CatsUtil catsUtil;
    @Inject
    ApiArguments apiArguments;
    @Inject
    ProcessingArguments processingArguments;

    private ServiceCaller serviceCaller;

    @BeforeAll
    public static void setup() {
        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.post("/pets").willReturn(WireMock.ok("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.put("/pets").willReturn(WireMock.aResponse().withBody("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.get("/pets/1").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
        wireMockServer.stubFor(WireMock.get("/pets/1?limit=2&no").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
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
        FilesArguments filesArguments = new FilesArguments(catsUtil);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        serviceCaller = new ServiceCaller(testCaseListener, catsUtil, filesArguments, catsDSLParser, authArguments, apiArguments, processingArguments);

        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(authArguments, "basicAuth", "user:password");
        ReflectionTestUtils.setField(filesArguments, "refDataFile", new File("src/test/resources/refFields.yml"));
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("src/test/resources/headers.yml"));
        ReflectionTestUtils.setField(filesArguments, "params", List.of("id=1", "test=2"));
        ReflectionTestUtils.setField(authArguments, "sslKeystore", null);
        ReflectionTestUtils.setField(authArguments, "proxyHost", null);
        ReflectionTestUtils.setField(authArguments, "proxyPort", 0);

        filesArguments.loadHeaders();
        filesArguments.loadRefData();
        filesArguments.loadURLParams();
    }

    @Test
    void shouldSetRateLimiter() {
        ReflectionTestUtils.setField(apiArguments, "maxRequestsPerMinute", 30);
        serviceCaller.initRateLimiter();
        serviceCaller.initHttpClient();

        long t0 = System.currentTimeMillis();
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());
        long t1 = System.currentTimeMillis();

        Assertions.assertThat(t1 - t0).isGreaterThan(3900);
    }

    @Test
    void shouldNotSetRateLimiter() {
        serviceCaller.initRateLimiter();
        serviceCaller.initHttpClient();

        long t0 = System.currentTimeMillis();
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());
        serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        long t1 = System.currentTimeMillis();

        Assertions.assertThat(t1 - t0).isLessThan(1000);
    }

    @Test
    void givenAServer_whenDoingADeleteCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.DELETE)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAHeadCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.HEAD)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPatchCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'id':'1'}").httpMethod(HttpMethod.PATCH)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();
    }

    @Test
    void givenAServer_whenDoingATraceCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}").httpMethod(HttpMethod.TRACE)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPostCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}").httpMethod(HttpMethod.POST)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAPostCallAndServerUnavailable_thenProperDetailsAreBeingReturned() {
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:111");

        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        ServiceData data = ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}").httpMethod(HttpMethod.POST)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build();
        Assertions.assertThatThrownBy(() -> serviceCaller.call(data)).isInstanceOf(CatsIOException.class);
    }

    @Test
    void givenAServer_whenDoingAPutCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets").payload("{'field':'newValue'}").httpMethod(HttpMethod.PUT)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAGetCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        CatsResponse catsResponse = serviceCaller.call(ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1','limit':2,'no':null}").httpMethod(HttpMethod.GET)
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build()))
                .queryParams(Set.of("limit", "no")).build());

        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/pets/1?limit=2&no")));
        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'pet':'pet'}");
    }

    @Test
    void shouldRemoveRefDataFieldsWhichAreMarkedForRemoval() {
        serviceCaller.initHttpClient();
        serviceCaller.initRateLimiter();

        ServiceData data = ServiceData.builder().relativePath("/pets").payload("{\"id\":\"1\", \"field\":\"old_value\",\"name\":\"cats\"}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build();
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
        List<CatsRequest.Header> headers = serviceCaller.buildHeaders(data);
        Optional<CatsRequest.Header> catsHeader = headers.stream().filter(header -> header.getName().equalsIgnoreCase("catsHeader")).findFirst();

        Assertions.assertThat(catsHeader).isEmpty();
    }

    @Test
    void shouldMergeFuzzingForSuppliedHeaders() {
        ServiceData data = ServiceData.builder().headers(Set.of(CatsHeader.builder().name("catsFuzzedHeader").value("  anotherValue").build()))
                .fuzzedHeader("catsFuzzedHeader").build();
        List<CatsRequest.Header> headers = serviceCaller.buildHeaders(data);
        List<CatsRequest.Header> catsHeader = headers.stream().filter(header -> header.getName().equalsIgnoreCase("catsFuzzedHeader")).collect(Collectors.toList());

        Assertions.assertThat(catsHeader).hasSize(1);
        Assertions.assertThat(catsHeader.get(0).getValue()).isEqualTo("  cats");
    }

    @Test
    void shouldAddHeaderWhenAddUserHeadersOffButSuppliedInHeadersFile() {
        ServiceData data = ServiceData.builder()
                .headers(Set.of(CatsHeader.builder().name("simpleHeader").value("simpleValue").build(), CatsHeader.builder().name("catsFuzzedHeader").value("anotherValue").build()))
                .fuzzedHeader("catsFuzzedHeader").addUserHeaders(false).build();

        List<CatsRequest.Header> headers = serviceCaller.buildHeaders(data);
        List<String> headerNames = headers.stream().map(CatsRequest.Header::getName).collect(Collectors.toList());
        Assertions.assertThat(headerNames).doesNotContain("header").contains("catsFuzzedHeader", "simpleHeader");

        List<CatsRequest.Header> catsHeader = headers.stream().filter(header -> header.getName().equalsIgnoreCase("catsFuzzedHeader")).collect(Collectors.toList());
        Assertions.assertThat(catsHeader).hasSize(1);
        Assertions.assertThat(catsHeader.get(0).getValue()).isEqualTo("cats");
    }

    @Test
    void shouldAddHeaderWhenAddUserHeadersOffButAuthenticationHeader() {
        ServiceData data = ServiceData.builder()
                .headers(Set.of(CatsHeader.builder().name("simpleHeader").value("simpleValue").build()))
                .relativePath("auth-header")
                .fuzzedHeader("catsFuzzedHeader").addUserHeaders(false).build();

        List<CatsRequest.Header> headers = serviceCaller.buildHeaders(data);
        List<String> headerNames = headers.stream().map(CatsRequest.Header::getName).collect(Collectors.toList());
        Assertions.assertThat(headerNames).doesNotContain("header", "catsFuzzedHeader").contains("simpleHeader", "jwt");
    }
}
