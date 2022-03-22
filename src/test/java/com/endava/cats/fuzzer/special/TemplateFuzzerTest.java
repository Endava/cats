package com.endava.cats.fuzzer.special;

import com.endava.cats.args.UserArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@QuarkusTest
class TemplateFuzzerTest {
    private UserArguments userArguments;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CatsUtil catsUtil;

    private TemplateFuzzer templateFuzzer;

    @BeforeEach
    void setup() {
        userArguments = Mockito.mock(UserArguments.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        catsUtil = new CatsUtil(new CatsDSLParser());
        serviceCaller = Mockito.mock(ServiceCaller.class);
        templateFuzzer = new TemplateFuzzer(serviceCaller, testCaseListener, catsUtil, userArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunWhenNoTargetFields() {
        FuzzingData data = FuzzingData.builder().targetFields(Collections.emptySet()).build();

        templateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotRunWhenTargetFieldsButNoMatch() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("test"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunWhenTargetFieldInPayload() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("field"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(45)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());
    }

    @Test
    void shouldRunWhenTargetFieldInHeader() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(45)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());

    }

    @Test
    void shouldNotRunWhenUserDictionaryButInvalidFile() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .build();
        Mockito.when(userArguments.getWords()).thenReturn(new File("non_real"));
        templateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunWithUserDictionary() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .build();
        Mockito.when(userArguments.getWords()).thenReturn(new File("src/test/resources/dict.txt"));
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());
    }

    @Test
    void shouldReportErrorWhenServiceException() throws Exception {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .build();
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenThrow(IOException.class);
        Mockito.when(userArguments.getWords()).thenReturn(new File("src/test/resources/dict.txt"));

        templateFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportError(Mockito.any(), Mockito.eq("Something went wrong {}"), Mockito.any());
    }

}
