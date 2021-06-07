package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
class MinLengthExactValuesInStringFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private MinLengthExactValuesInStringFieldsFuzzer minLengthExactValuesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        minLengthExactValuesInStringFieldsFuzzer = new MinLengthExactValuesInStringFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact minLength size values");
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer  .skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMinLength(2);
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(minLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}
