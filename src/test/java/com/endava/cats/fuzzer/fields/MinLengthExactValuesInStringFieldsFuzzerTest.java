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

class MinLengthExactValuesInStringFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private MinLengthExactValuesInStringFieldsFuzzer minLengthExactValuesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        minLengthExactValuesInStringFieldsFuzzer = new MinLengthExactValuesInStringFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined(stringSchema)).isFalse();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact minLength size values");
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer  .skipFor()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMinLength(2);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined(stringSchema)).isTrue();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}
