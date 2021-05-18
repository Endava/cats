package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.Collections;

@ExtendWith(SpringExtension.class)
class MinimumExactValuesInNumericFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private MinimumExactValuesInNumericFieldsFuzzer minimumExactValuesInNumericFieldsFuzzer;

    @Mock
    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        minimumExactValuesInNumericFieldsFuzzer = new MinimumExactValuesInNumericFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsOnly(IntegerSchema.class, NumberSchema.class);
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact minimum size values");
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.skipFor()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMinimum(BigDecimal.TEN);
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(minimumExactValuesInNumericFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}
