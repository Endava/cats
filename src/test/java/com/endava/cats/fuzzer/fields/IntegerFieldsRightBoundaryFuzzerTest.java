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
public class IntegerFieldsRightBoundaryFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private IntegerFieldsRightBoundaryFuzzer integerFieldsRightBoundaryFuzzer;

    @BeforeEach
    public void setup() {
        integerFieldsRightBoundaryFuzzer = new IntegerFieldsRightBoundaryFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewIntegerFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFieldsRightBoundaryFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(integerFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.hasBoundaryDefined(nrSchema)).isTrue();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.description()).isNotNull();
    }
}
