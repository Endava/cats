package com.endava.cats.model.generator.impl;


import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BinaryGeneratorTest {


    @ParameterizedTest
    @CsvSource({"byte,true", "binary,true", "not,false"})
    void shouldTestForApply(String format, boolean expected) {
        BinaryGenerator binaryGenerator = new BinaryGenerator();
        Assertions.assertThat(binaryGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

}
