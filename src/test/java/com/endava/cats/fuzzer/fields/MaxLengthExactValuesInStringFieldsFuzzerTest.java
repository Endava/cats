package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
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
class MaxLengthExactValuesInStringFieldsFuzzerTest {

    private MaxLengthExactValuesInStringFieldsFuzzer maxLengthExactValuesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        maxLengthExactValuesInStringFieldsFuzzer = new MaxLengthExactValuesInStringFieldsFuzzer(null, null, filesArguments);
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
    }


    @Test
    void shouldApply() {
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).containsOnly("string");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
    }

    @Test
    void shouldHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMaxLength(20);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
    }

    @Test
    void shouldSkipForDeleteAndGet() {
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void shouldReturnDataToSendToTheService() {
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact maxLength size values");
    }

    @Test
    void shouldHaveBoundaryValue() {
        StringSchema stringSchema = new StringSchema();
        stringSchema.setMaxLength(20);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }

    @Test
    void shouldReturn2XXForExpectedResultCodes() {
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @ParameterizedTest
    @CsvSource({",true", "mama,false"})
    void shouldTestBoundaryDefinedBasedOnFormat(String format, boolean expected) {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        stringSchema.setMaxLength(20);
        stringSchema.setFormat(format);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isEqualTo(expected);
    }
}
