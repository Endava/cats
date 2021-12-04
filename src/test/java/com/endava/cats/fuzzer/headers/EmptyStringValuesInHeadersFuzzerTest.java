package com.endava.cats.fuzzer.headers;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EmptyStringValuesInHeadersFuzzerTest {
    private EmptyStringValuesInHeadersFuzzer emptyStringValuesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        emptyStringValuesInHeadersFuzzer = new EmptyStringValuesInHeadersFuzzer(null, null);
    }

    @Test
    void givenANewEmptyStringValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheEmptyStringValuesInHeadersFuzzer() {
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(emptyStringValuesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
    }
}
