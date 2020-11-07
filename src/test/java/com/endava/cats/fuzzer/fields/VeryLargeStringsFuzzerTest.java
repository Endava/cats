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

@ExtendWith(SpringExtension.class)
class VeryLargeStringsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private CatsParams catsParams;

    private VeryLargeStringsFuzzer veryLargeStringsFuzzer;

    @BeforeEach
    void setup() {
        veryLargeStringsFuzzer = new VeryLargeStringsFuzzer(serviceCaller, testCaseListener, catsUtil, catsParams);
    }

    @Test
    void givenANewVeryLargeStringsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheVeryLargeStringsFuzzer() {
        StringSchema nrSchema = new StringSchema();
        Assertions.assertThat(veryLargeStringsFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsExactly(StringSchema.class);
        Assertions.assertThat(veryLargeStringsFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(veryLargeStringsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(veryLargeStringsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeStringsFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
