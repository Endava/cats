package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
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
public class ExtremeNegativeValueIntegerFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private ExtremeNegativeValueIntegerFieldsFuzzer extremeNegativeValueIntegerFieldsFuzzer;

    @BeforeEach
    public void setup() {
        extremeNegativeValueIntegerFieldsFuzzer = new ExtremeNegativeValueIntegerFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewExtremeNegativeValueIntegerFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(extremeNegativeValueIntegerFieldsFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.hasBoundaryDefined(nrSchema)).isTrue();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
