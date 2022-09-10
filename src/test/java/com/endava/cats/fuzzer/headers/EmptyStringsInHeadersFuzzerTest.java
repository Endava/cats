package com.endava.cats.fuzzer.headers;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EmptyStringsInHeadersFuzzerTest {
    private EmptyStringsInHeadersFuzzer emptyStringsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        emptyStringsInHeadersFuzzer = new EmptyStringsInHeadersFuzzer(null, null);
    }

    @Test
    void givenANewEmptyStringValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheEmptyStringValuesInHeadersFuzzer() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(emptyStringsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(emptyStringsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
    }
}
