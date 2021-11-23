package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class VeryLargeValuesInFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    @Mock
    private ProcessingArguments processingArguments;

    private VeryLargeValuesInFieldsFuzzer veryLargeValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        veryLargeValuesInFieldsFuzzer = new VeryLargeValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, processingArguments);
    }

    @Test
    void givenANewVeryLargeStringsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheVeryLargeStringsFuzzer() {
        Assertions.assertThat(veryLargeValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();

    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData()).hasSize(20000);
    }
}
