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
class MaxLengthExactValuesInStringFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private MaxLengthExactValuesInStringFieldsFuzzer maxLengthExactValuesInStringFieldsFuzzer;

    @Mock
    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        maxLengthExactValuesInStringFieldsFuzzer = new MaxLengthExactValuesInStringFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Collections.emptyMap());
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualByComparingTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("exact maxLength size values");
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.skipFor()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);

        stringSchema.setMaxLength(2);
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(maxLengthExactValuesInStringFieldsFuzzer.getBoundaryValue(stringSchema)).isNotNull();
    }
}
