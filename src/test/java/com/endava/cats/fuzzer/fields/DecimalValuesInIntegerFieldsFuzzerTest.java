package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.apache.commons.lang3.math.NumberUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class DecimalValuesInIntegerFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private DecimalValuesInIntegerFieldsFuzzer decimalValuesInIntegerFieldsFuzzer;

    @BeforeEach
    void setup() {
        decimalValuesInIntegerFieldsFuzzer = new DecimalValuesInIntegerFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenANewDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(decimalValuesInIntegerFieldsFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
