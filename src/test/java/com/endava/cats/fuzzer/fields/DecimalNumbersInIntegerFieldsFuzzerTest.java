package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
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
class DecimalNumbersInIntegerFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private ProcessingArguments processingArguments;
    private DecimalNumbersInIntegerFieldsFuzzer decimalNumbersInIntegerFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        decimalNumbersInIntegerFieldsFuzzer = new DecimalNumbersInIntegerFieldsFuzzer(
                serviceCaller, testCaseListener, filesArguments, processingArguments);
    }

    @Test
    void givenANewDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("integer"))).isTrue();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(Double.class);
    }

    @ParameterizedTest
    @CsvSource({"true,FOURXX", "false,FOURXX_TWOXX"})
    void shouldConsiderStrictTypesForDecimalInIntegerField(boolean strictTypes, ResponseCodeFamilyPredefined expectedCode) {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(strictTypes);
        Mockito.when(filesArguments.getRefData("/orders")).thenReturn(Map.of());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).body("{}").build());

        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/orders")
                .headers(Set.of())
                .payload("{\"count\":5}")
                .allFields(Set.of("count"))
                .allRequiredFields(List.of("count"))
                .reqSchema(new IntegerSchema())
                .requestPropertyTypes(Map.of("count", new IntegerSchema()))
                .requestContentTypes(List.of("application/json"))
                .queryParams(Set.of())
                .responseCodes(Set.of("200", "400"))
                .build();

        decimalNumbersInIntegerFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.eq(expectedCode), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
