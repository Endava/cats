package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
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
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class ReplaceArraysWithPrimitivesFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    ProcessingArguments processingArguments;
    private ReplaceArraysWithPrimitivesFieldsFuzzer replaceArraysWithPrimitivesFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        replaceArraysWithPrimitivesFieldsFuzzer = new ReplaceArraysWithPrimitivesFieldsFuzzer(catsExecutor, processingArguments);
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

    @ParameterizedTest
    @CsvSource({
            "string,false,1,true,none,FOURXX",
            "string,false,1,false,none,FOURXX_TWOXX",
            "string,false,2,false,none,FOURXX",
            "string,true,1,false,none,FOURXX",
            "string,false,1,false,maxLength,FOURXX",
            "string,false,1,false,minLength,FOURXX",
            "string,false,1,false,pattern,FOURXX",
            "string,false,1,false,format,FOURXX",
            "string,false,1,false,const,FOURXX",
            "integer,false,1,false,none,FOURXX"
    })
    void shouldConsiderStrictTypesOnlyForUnconstrainedStringArrays(
            String itemType, boolean hasEnum, int minItems, boolean strictTypes, String extraConstraint, ResponseCodeFamilyPredefined expectedCode) {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(strictTypes);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());

        ArraySchema arraySchema = new ArraySchema();
        arraySchema.setMinItems(minItems);
        if ("integer".equals(itemType)) {
            arraySchema.setItems(new IntegerSchema());
        } else {
            StringSchema itemSchema = new StringSchema();
            if (hasEnum) {
                itemSchema.setEnum(List.of("A", "B"));
            }
            switch (extraConstraint) {
                case "maxLength" -> itemSchema.setMaxLength(5);
                case "minLength" -> itemSchema.setMinLength(30);
                case "pattern" -> itemSchema.setPattern("^[A-Z]+$");
                case "format" -> itemSchema.setFormat("uuid");
                case "const" -> itemSchema.setConst("fixed");
                default -> { }
            }
            arraySchema.setItems(itemSchema);
        }

        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.POST)
                .payload("{\"tags\":[\"A\"]}")
                .allFields(Set.of("tags"))
                .requestPropertyTypes(Map.of("tags", arraySchema))
                .schemaMap(Map.of())
                .requestContentTypes(List.of("application/json"))
                .build();

        replaceArraysWithPrimitivesFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(expectedCode));
    }
}
