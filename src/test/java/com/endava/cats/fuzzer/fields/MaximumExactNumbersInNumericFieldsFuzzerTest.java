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
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;

@QuarkusTest
class MaximumExactNumbersInNumericFieldsFuzzerTest {
    private MaximumExactNumbersInNumericFieldsFuzzer maximumExactNumbersInNumericFieldsFuzzer;

    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        maximumExactNumbersInNumericFieldsFuzzer = new MaximumExactNumbersInNumericFieldsFuzzer(null, null, null, filesArguments);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).containsOnly("integer", "number");
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact maximum size values");
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMaximum(BigDecimal.TEN);
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(maximumExactNumbersInNumericFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}