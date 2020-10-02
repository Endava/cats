package com.endava.cats.io;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsParams;
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
@SpringJUnitConfig({CatsUtil.class})
class ServiceCallerTest {

    public static WireMockServer wireMockServer;
    @MockBean
    private TestCaseListener testCaseListener;
    @MockBean
    private CatsDSLParser catsDSLParser;
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
        CatsParams catsParams = new CatsParams(catsUtil);
        serviceCaller = new ServiceCaller(testCaseListener, catsUtil, catsParams, catsDSLParser);

        ReflectionTestUtils.setField(serviceCaller, "server", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(catsParams, "refDataFile", "src/test/resources/refFields.yml");
        ReflectionTestUtils.setField(catsParams, "headersFile", "src/test/resources/headers.yml");
        ReflectionTestUtils.setField(catsParams, "params", "id=1,test=2");

        catsParams.loadHeaders();
        catsParams.loadRefData();
        catsParams.loadURLParams();
    }

    @Test
    void givenAServer_whenDoingADeleteCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.DELETE, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAHeadCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.HEAD, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPatchCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.PATCH, ServiceData.builder().relativePath("/pets").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();
    }

    @Test
    void givenAServer_whenDoingATraceCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.TRACE, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEmpty();

    }

    @Test
    void givenAServer_whenDoingAPostCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.POST, ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAPutCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.PUT, ServiceData.builder().relativePath("/pets").payload("{'field':'newValue'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");
    }

    @Test
    void givenAServer_whenDoingAGetCall_thenProperDetailsAreBeingReturned() {
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.GET, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'pet':'pet'}");
    }
}
