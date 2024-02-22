package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Collections;

@QuarkusTest
class MinLengthExactValuesInStringFieldsFuzzerTest {

    private MinLengthExactValuesInStringFieldsFuzzer minLengthExactValuesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        minLengthExactValuesInStringFieldsFuzzer = new MinLengthExactValuesInStringFieldsFuzzer(null, null, filesArguments);
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
    }

    @Test
    void shouldApply() {
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).containsOnly("string");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
    }

    @Test
    void shouldHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMinLength(20);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
    }

    @Test
    void shouldSkipForDeleteAndGet() {
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void shouldReturnDataToSendToTheService() {
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact minLength size values");
    }

    @Test
    void shouldHaveBoundaryValue() {
        StringSchema stringSchema = new StringSchema();
        stringSchema.setMinLength(20);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }

    @Test
    void shouldReturn2XXForExpectedResultCodes() {
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
    }

    @ParameterizedTest
    @CsvSource({",true", "mama,false"})
    void shouldTestBoundaryDefinedBasedOnFormat(String format, boolean expected) {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMinLength(20);
        stringSchema.setFormat(format);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isEqualTo(expected);
    }
}
