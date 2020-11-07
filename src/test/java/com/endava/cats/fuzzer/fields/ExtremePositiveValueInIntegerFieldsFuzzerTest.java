package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
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
class ExtremePositiveValueInIntegerFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private CatsParams catsParams;

    private ExtremePositiveValueInIntegerFieldsFuzzer extremePositiveValueInIntegerFields;

    @BeforeEach
    void setup() {
        extremePositiveValueInIntegerFields = new ExtremePositiveValueInIntegerFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, catsParams);
    }

    @Test
    void givenANewExtremePositiveValueIntegerFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremePositiveValueInIntegerFields.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(extremePositiveValueInIntegerFields.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(extremePositiveValueInIntegerFields.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremePositiveValueInIntegerFields.description()).isNotNull();
        Assertions.assertThat(extremePositiveValueInIntegerFields.typeOfDataSentToTheService()).isNotNull();
    }
}
