package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.BooleanSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class BooleanFieldsFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;

    private BooleanFieldsFuzzer booleanFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
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
