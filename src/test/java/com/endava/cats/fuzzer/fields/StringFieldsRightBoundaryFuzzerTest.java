package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
class StringFieldsRightBoundaryFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private CatsParams catsParams;

    private StringFieldsRightBoundaryFuzzer stringFieldsRightBoundaryFuzzer;

    @BeforeEach
    void setup() {
        stringFieldsRightBoundaryFuzzer = new StringFieldsRightBoundaryFuzzer(serviceCaller, testCaseListener, catsUtil, catsParams);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema nrSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", nrSchema)).build();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.description()).isNotNull();

        nrSchema.setMaxLength(2);
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", data)).isTrue();

    }

}
