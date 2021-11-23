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
class VeryLargeUnicodeValuesInFieldsFuzzerTest {
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

    private VeryLargeUnicodeValuesInFieldsFuzzer veryLargeUnicodeValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        veryLargeUnicodeValuesInFieldsFuzzer = new VeryLargeUnicodeValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, processingArguments);
    }

    @Test
    void shouldOverrideDefaultMethods() {
        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData()).hasSizeGreaterThan(20000);
    }

    @Test
    void shouldGenerateLessThan500() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20);
        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData()).hasSize(20 + "cats".length());
    }
}