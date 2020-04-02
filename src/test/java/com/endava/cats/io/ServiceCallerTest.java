package com.endava.cats.io;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
public class ServiceCallerTest {

    public static WireMockServer wireMockServer;

    @MockBean
    private TestCaseListener testCaseListener;

    @MockBean
    private CatsUtil catsUtil;
    private ServiceCaller serviceCaller;

    @BeforeAll
    public static void setup() {
        wireMockServer = new WireMockServer(8888);
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.post("/pets").willReturn(WireMock.aResponse().withBody("{'result':'OK'}")));
        wireMockServer.stubFor(WireMock.get("/pets/1").willReturn(WireMock.aResponse().withBody("{'pet':'pet'}")));
    }

    @AfterAll
    public static void clean() {
        wireMockServer.stop();
    }

    @BeforeEach
    public void setupServiceCaller() {
        serviceCaller = new ServiceCaller(testCaseListener, catsUtil);
    }

    @Test
    public void givenAServer_whenDoingAPostCall_thenProperDetailsAreBeingReturned() throws Exception {
        ReflectionTestUtils.setField(serviceCaller, "server", "http://localhost:8888");
        ReflectionTestUtils.setField(serviceCaller, "refDataFile", "src/test/resources/refFields.yml");
        ReflectionTestUtils.setField(serviceCaller, "headersFile", "src/test/resources/headers.yml");
        Mockito.doCallRealMethod().when(catsUtil).parseYaml(Mockito.any());

        serviceCaller.loadHeaders();
        serviceCaller.loadRefData();

        Mockito.doCallRealMethod().when(catsUtil).parseAsJsonElement(Mockito.anyString());
        Mockito.doCallRealMethod().when(catsUtil).isValidJson(Mockito.anyString());
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.POST, ServiceData.builder().relativePath("/pets").payload("{'field':'oldValue'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'result':'OK'}");

    }

    @Test
    public void givenAServer_whenDoingAGetCall_thenProperDetailsAreBeingReturned() throws Exception {
        ReflectionTestUtils.setField(serviceCaller, "server", "http://localhost:8888");
        ReflectionTestUtils.setField(serviceCaller, "refDataFile", "src/test/resources/refFields.yml");
        ReflectionTestUtils.setField(serviceCaller, "headersFile", "src/test/resources/headers.yml");
        Mockito.doCallRealMethod().when(catsUtil).parseYaml(Mockito.any());

        serviceCaller.loadHeaders();
        serviceCaller.loadRefData();

        Mockito.doCallRealMethod().when(catsUtil).parseAsJsonElement(Mockito.anyString());
        Mockito.doCallRealMethod().when(catsUtil).isValidJson(Mockito.anyString());
        CatsResponse catsResponse = serviceCaller.call(HttpMethod.GET, ServiceData.builder().relativePath("/pets/{id}").payload("{'id':'1'}")
                .headers(Collections.singleton(CatsHeader.builder().name("header").value("header").build())).build());

        Assertions.assertThat(catsResponse.responseCodeAsString()).isEqualTo("200");
        Assertions.assertThat(catsResponse.getBody()).isEqualTo("{'pet':'pet'}");
    }
}
