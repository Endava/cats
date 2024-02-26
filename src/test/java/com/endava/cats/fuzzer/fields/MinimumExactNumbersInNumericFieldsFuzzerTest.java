package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
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

@QuarkusTest
class MinimumExactNumbersInNumericFieldsFuzzerTest {
    private MinimumExactNumbersInNumericFieldsFuzzer minimumExactNumbersInNumericFieldsFuzzer;

    @BeforeEach
    void setup() {
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        minimumExactNumbersInNumericFieldsFuzzer = new MinimumExactNumbersInNumericFieldsFuzzer(null, null, filesArguments);
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
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
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
    }

    @ParameterizedTest
    @CsvSource({",true", "mama,false"})
    void shouldTestBoundaryDefinedBasedOnFormat(String format, boolean expected) {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMinimum(BigDecimal.TEN);
        stringSchema.setFormat(format);
        Assertions.assertThat(minimumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isEqualTo(expected);
    }
}
