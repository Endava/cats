package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class StringFieldsRightBoundaryFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private StringFieldsRightBoundaryFuzzer stringFieldsRightBoundaryFuzzer;

    @BeforeEach
    void setup() {
        stringFieldsRightBoundaryFuzzer = new StringFieldsRightBoundaryFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema nrSchema = new StringSchema();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined(nrSchema)).isFalse();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.description()).isNotNull();

        nrSchema.setMaxLength(2);
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined(nrSchema)).isTrue();

    }

}
