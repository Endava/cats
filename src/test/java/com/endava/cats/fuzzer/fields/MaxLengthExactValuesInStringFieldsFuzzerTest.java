package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class MaxLengthExactValuesInStringFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private MaxLengthExactValuesInStringFieldsFuzzer maxLengthExactValuesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        maxLengthExactValuesInStringFieldsFuzzer = new MaxLengthExactValuesInStringFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined(stringSchema)).isFalse();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact maxLength size values");
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.skipFor()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMaxLength(2);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined(stringSchema)).isTrue();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}
