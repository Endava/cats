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

import java.util.Collections;

@QuarkusTest
class MinLengthExactValuesInStringFieldsFuzzerTest {
    private FilesArguments filesArguments;

    private MinLengthExactValuesInStringFieldsFuzzer minLengthExactValuesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        minLengthExactValuesInStringFieldsFuzzer = new MinLengthExactValuesInStringFieldsFuzzer(null, null, null, filesArguments);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("string"))).isTrue();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact minLength size values");
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMinLength(2);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}
