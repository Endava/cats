package com.endava.cats.fuzzer.headers;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class NullValuesInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private NullValuesInHeadersFuzzer nullValuesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        nullValuesInHeadersFuzzer = new NullValuesInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewNullValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheNullValuesInHeadersFuzzer() {
        Assertions.assertThat(nullValuesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(nullValuesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(nullValuesInHeadersFuzzer.fuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(nullValuesInHeadersFuzzer.fuzzStrategy().getData()).isNull();

    }
}
