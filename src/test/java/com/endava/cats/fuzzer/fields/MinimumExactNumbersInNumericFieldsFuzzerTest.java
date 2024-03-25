package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@QuarkusTest
class MinimumExactNumbersInNumericFieldsFuzzerTest {
    private MinimumExactNumbersInNumericFieldsFuzzer minimumExactNumbersInNumericFieldsFuzzer;
    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        minimumExactNumbersInNumericFieldsFuzzer = new MinimumExactNumbersInNumericFieldsFuzzer(null, null, filesArguments);
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
    }

    @Test
    void shouldNotRunWhenRefData() {
        Mockito.when(filesArguments.getRefData("/test")).thenReturn(Map.of("test", "value"));
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().path("/test").requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMaximum(new BigDecimal(100));
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
    }

    @Test
    void shouldApply() {
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).containsOnly("integer", "number");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
    }

    @Test
    void shouldHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMinimum(BigDecimal.TEN);
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
    }

    @Test
    void shouldSkipForDeleteAndGet() {
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void shouldReturnDataToSendToTheService() {
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact minimum size values");
    }

    @Test
    void shouldHaveBoundaryValue() {
        StringSchema stringSchema = new StringSchema();
        stringSchema.setMinimum(BigDecimal.TEN);
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }

    @Test
    void shouldGenerateNumberBoundaryValue() {
        IntegerSchema schema = new IntegerSchema();
        schema.setMinimum(BigDecimal.ONE);
        Object generated = minimumExactNumbersInNumericFieldsFuzzer.getBoundaryValue(schema);

        Assertions.assertThat(generated).isInstanceOf(Number.class);
    }

    @Test
    void shouldReturn2XXForExpectedResultCodes() {
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @ParameterizedTest
    @CsvSource({",true", "mama,true"})
    void shouldTestBoundaryDefinedBasedOnFormat(String format, boolean expected) {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMinimum(BigDecimal.TEN);
        stringSchema.setFormat(format);
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isEqualTo(expected);
    }
}
