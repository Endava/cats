package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@QuarkusTest
class ReplaceArraysWithPrimitivesFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private ReplaceArraysWithPrimitivesFieldsFuzzer replaceArraysWithPrimitivesFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        replaceArraysWithPrimitivesFieldsFuzzer = new ReplaceArraysWithPrimitivesFieldsFuzzer(catsExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(replaceArraysWithPrimitivesFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(replaceArraysWithPrimitivesFieldsFuzzer).hasToString("ReplaceArraysWithPrimitivesFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(replaceArraysWithPrimitivesFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSkipIfFieldPrimitive() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("primitiveField"));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"primitiveField": 3}
                """);
        replaceArraysWithPrimitivesFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunIfFieldArray() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("arrayField"));
        Mockito.when(data.getPayload()).thenReturn("""
                   {"arrayField": [{
                            "inner": "innerValue"
                        }]
                    }
                """);
        replaceArraysWithPrimitivesFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }


    @Test
    void shouldSkipIfFieldObjectAndNotArray() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("objectField"));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"objectField": {
                            "inner": "innerValue"
                        }
                    }
                """);
        replaceArraysWithPrimitivesFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }
}
