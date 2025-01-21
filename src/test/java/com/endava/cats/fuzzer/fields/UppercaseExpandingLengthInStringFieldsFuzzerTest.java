package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
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
class UppercaseExpandingLengthInStringFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    UppercaseExpandingLengthInStringFieldsFuzzer uppercaseExpandingLengthInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        uppercaseExpandingLengthInStringFieldsFuzzer = new UppercaseExpandingLengthInStringFieldsFuzzer(testCaseListener, catsExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(uppercaseExpandingLengthInStringFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(uppercaseExpandingLengthInStringFieldsFuzzer).hasToString("UppercaseExpandingLengthInStringFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(uppercaseExpandingLengthInStringFieldsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldSkipIfNotStringSchema() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", new Schema<>().type("integer")));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        uppercaseExpandingLengthInStringFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipIfFieldNotInPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myFieldNotInPayload"));
        Schema<String> myStringSchema = new Schema<>().type("string");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myFieldNotInPayload", myStringSchema));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        uppercaseExpandingLengthInStringFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @ParameterizedTest
    @CsvSource(value = {"5", "null"}, nullValues = "null")
    void shouldReplaceIfStringSchemaAndFieldInPayload(Integer length) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Schema<String> myStringSchema = new Schema<>().type("string").maxLength(length);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("objectField#myField", myStringSchema));
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("objectField#myField"));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"objectField": {
                            "myField": "innerValue"
                        }
                    }
                """);
        uppercaseExpandingLengthInStringFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(new ResponseCodeFamilyDynamic(List.of("2XX", "4XX"))));
    }
}
