package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class PathTraversalInStringFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private SecurityFuzzerArguments securityFuzzerArguments;
    private PathTraversalInStringFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        securityFuzzerArguments = new SecurityFuzzerArguments();
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new PathTraversalInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Test
    void shouldProvidePayloadsAndMetadata() {
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("path traversal");
        Assertions.assertThat(fuzzer).hasToString("PathTraversalInStringFieldsFuzzer");
        Assertions.assertThat(fuzzer.getTopInjectionPayloads()).hasSize(10);
        Assertions.assertThat(fuzzer.getAllInjectionPayloads())
                .containsAll(fuzzer.getTopInjectionPayloads())
                .hasSizeGreaterThan(10);
    }

    @ParameterizedTest
    @CsvSource({
            "200, 'root:x:0:0:root:/root:/bin/bash', true",
            "200, '[fonts]\n[extensions]', true",
            "200, 'PATH=/usr/bin\nHOME=/root\nUSER=root', true",
            "200, 'java.nio.file.NoSuchFileException: /etc/passwd', true",
            "500, 'FileNotFoundError: [Errno 2] No such file or directory: /etc/passwd', true",
            "500, 'System.IO.DirectoryNotFoundException: Could not find a part of the path', true",
            "500, 'Warning: fopen(../../../../etc/passwd): failed to open stream: No such file or directory', true",
            "500, 'open ../../../../etc/passwd: no such file or directory', true",
            "500, 'Errno::ENOENT: No such file or directory @ rb_sysopen', true",
            "400, '{\"error\":\"Invalid path\"}', false",
            "200, '{\"status\":\"ok\"}', false"
    })
    void shouldDetectPathTraversalIndicators(int statusCode, String responseBody, boolean expectError) {
        Map<String, Schema> schemas = Map.of("filePath", new StringSchema());
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("filePath"));
        Mockito.when(data.getPayload()).thenReturn("{\"filePath\":\"report.pdf\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemas);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(statusCode).body(responseBody).build());

        fuzzer.fuzz(data);

        if (expectError) {
            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.eq(data), Mockito.contains("Path traversal"), Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any());
        }
    }
}
