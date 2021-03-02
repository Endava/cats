package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.BooleanSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class BooleanFieldsFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private BooleanFieldsFuzzer booleanFieldsFuzzer;

    @BeforeEach
    void setup() {
        booleanFieldsFuzzer = new BooleanFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewBooleanFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheBooleanFuzzer() {
        Assertions.assertThat(booleanFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(BooleanSchema.class))).isTrue();
        Assertions.assertThat(booleanFieldsFuzzer.getBoundaryValue(null)).isNotNull();
        Assertions.assertThat(booleanFieldsFuzzer.hasBoundaryDefined(null, FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(booleanFieldsFuzzer.description()).isNotNull();
    }
}
