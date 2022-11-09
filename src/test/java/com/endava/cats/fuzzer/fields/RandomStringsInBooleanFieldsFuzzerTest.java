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
class RandomStringsInBooleanFieldsFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;

    private RandomStringsInBooleanFieldsFuzzer randomStringsInBooleanFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        randomStringsInBooleanFieldsFuzzer = new RandomStringsInBooleanFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewBooleanFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheBooleanFuzzer() {
        Assertions.assertThat(randomStringsInBooleanFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("boolean"))).isTrue();
        Assertions.assertThat(randomStringsInBooleanFieldsFuzzer.getBoundaryValue(null)).isNotNull();
        Assertions.assertThat(randomStringsInBooleanFieldsFuzzer.hasBoundaryDefined(null, FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(randomStringsInBooleanFieldsFuzzer.description()).isNotNull();
    }
}
