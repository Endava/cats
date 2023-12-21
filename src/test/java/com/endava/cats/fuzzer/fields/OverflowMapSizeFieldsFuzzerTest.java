package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
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
class OverflowMapSizeFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    @InjectSpy
    CatsUtil catsUtil;
    FieldsIteratorExecutor catsExecutor;
    private OverflowMapSizeFieldsFuzzer overflowMapSizeFieldsFuzzer;

    @Inject
    ProcessingArguments processingArguments;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, catsUtil, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        overflowMapSizeFieldsFuzzer = new OverflowMapSizeFieldsFuzzer(catsExecutor, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(overflowMapSizeFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(overflowMapSizeFieldsFuzzer).hasToString("OverflowMapSizeFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(overflowMapSizeFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSkipIfFieldPrimitive() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("primitiveField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("primitiveField", new Schema()));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"primitiveField": 3}
                """);
        overflowMapSizeFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource(value = {"20", "null"}, nullValues = "null")
    void shouldRunIfFieldDictionary(Integer maxItems) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema mapSchema = new MapSchema().maxProperties(maxItems).additionalProperties(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("mapField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("mapField", mapSchema));
        Mockito.when(data.getPayload()).thenReturn("""
                   {"mapField": {
                            "inner": "innerValue"
                        }
                    }
                """);
        overflowMapSizeFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void shouldSkipWhenMapFieldButNotFound() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema mapSchema = new MapSchema().maxProperties(10).additionalProperties(true);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("notFound"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("notFound", mapSchema));
        Mockito.when(data.getPayload()).thenReturn("""
                   {"mapField": {
                            "inner": "innerValue"
                        }
                    }
                """);
        overflowMapSizeFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }


    @Test
    void shouldSkipIfFieldObjectAndNotDictionary() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("objectField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("objectField", new Schema()));

        Mockito.when(data.getPayload()).thenReturn("""
                    {"objectField": {
                            "inner": "innerValue"
                        }
                    }
                """);
        overflowMapSizeFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }
}
