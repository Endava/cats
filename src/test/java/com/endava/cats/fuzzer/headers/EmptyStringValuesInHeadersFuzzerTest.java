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
public class EmptyStringValuesInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private EmptyStringValuesInHeadersFuzzer emptyStringValuesInHeadersFuzzer;

    @BeforeEach
    public void setup() {
        emptyStringValuesInHeadersFuzzer = new EmptyStringValuesInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    public void givenANewEmptyStringValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheEmptyStringValuesInHeadersFuzzer() {
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.fuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.fuzzStrategy().getData()).isEqualTo("");

    }
}
