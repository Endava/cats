package com.endava.cats.io;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig({CatsUtil.class, CatsDSLParser.class, AuthArguments.class})
class ServiceCallerTest {

    public static WireMockServer wireMockServer;
    @MockBean
    private TestCaseListener testCaseListener;
    @Autowired
    private CatsDSLParser catsDSLParser;
    @Autowired
    private AuthArguments authArguments;
    @Autowired
    private CatsUtil catsUtil;
    private ServiceCaller serviceCaller;

    @BeforeAll
    public static void setup() {
        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.post("/pets").willReturn(WireMock.aResponse().withBody("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.put("/pets").willReturn(WireMock.aResponse().withBody("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.get("/pets/1").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
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
        serviceCaller = new ServiceCaller(testCaseListener, catsUtil, filesArguments, catsDSLParser, authArguments);

        ReflectionTestUtils.setField(serviceCaller, "server", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(serviceCaller, "proxyHost", "empty");
        ReflectionTestUtils.setField(authArguments, "sslKeystore", "empty");
        ReflectionTestUtils.setField(authArguments, "basicAuth", "user:password");
        ReflectionTestUtils.setField(filesArguments, "refDataFile", "src/test/resources/refFields.yml");
        ReflectionTestUtils.setField(filesArguments, "headersFile", "src/test/resources/headers.yml");
        ReflectionTestUtils.setField(filesArguments, "params", "id=1,test=2");

        filesArguments.loadHeaders();
        filesArguments.loadRefData();
        filesArguments.loadURLParams();
    }

    @Test
    void givenAServer_whenDoingADeleteCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.DELETE, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAHeadCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.HEAD, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPatchCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.PATCH, ServiceData.builder().relativePath("/pets").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();
    }

    @Test
    void givenAServer_whenDoingATraceCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.TRACE, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPostCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.POST, ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAPostCallAndServerUnavailable_thenProperDetailsAreBeingReturned() {
        ReflectionTestUtils.setField(serviceCaller, "server", "http://localhost:111");

        serviceCaller.initHttpClient();
        ServiceData data = ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build();
        Assertions.assertThatThrownBy(() -> serviceCaller.call(HttpMethod.POST, data)).isInstanceOf(CatsIOException.class);
    }

    @Test
    void givenAServer_whenDoingAPutCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.PUT, ServiceData.builder().relativePath("/pets").payload("{'field':'newValue'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAGetCall_thenProperDetailsAreBeingReturned() {
        serviceCaller.initHttpClient();

        CatsResponse catsResponse = serviceCaller.call(HttpMethod.GET, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'pet':'pet'}");
    }

    @Test
    void shouldRemoveRefDataFieldsWhichAreMarkedForRemoval() {
        serviceCaller.initHttpClient();

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
        Assertions.assertThat(serviceCaller.httpClient).isNotNull();
    }

    @Test
    void shouldNotCreateSSLFactoryWhenKeystoreInvalid() {
        ReflectionTestUtils.setField(authArguments, "sslKeystore", "src/test/resources/cats_bad.jks");

        serviceCaller.initHttpClient();
        Assertions.assertThat(serviceCaller.httpClient).isNull();
    }

    @Test
    void shouldNotCreateSSLFactoryWhenKeystoreEmpty() {
        ReflectionTestUtils.setField(authArguments, "sslKeystore", "empty");

        serviceCaller.initHttpClient();
        Assertions.assertThat(serviceCaller.httpClient).isNotNull();
    }
}
