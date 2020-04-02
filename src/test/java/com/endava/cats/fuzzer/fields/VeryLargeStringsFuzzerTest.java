package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class VeryLargeStringsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private VeryLargeStringsFuzzer veryLargeStringsFuzzer;

    @BeforeEach
    public void setup() {
        veryLargeStringsFuzzer = new VeryLargeStringsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewVeryLargeStringsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheVeryLargeStringsFuzzer() {
        StringSchema nrSchema = new StringSchema();
        Assertions.assertThat(veryLargeStringsFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsExactly(StringSchema.class);
        Assertions.assertThat(veryLargeStringsFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(veryLargeStringsFuzzer.hasBoundaryDefined(nrSchema)).isTrue();
        Assertions.assertThat(veryLargeStringsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeStringsFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
