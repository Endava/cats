package com.endava.cats.generator.format.impl;


import com.endava.cats.generator.format.impl.BinaryGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.codec.binary.Base64;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BinaryGeneratorTest {
    @Test
    void givenAByteFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        BinaryGenerator strategy = new BinaryGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("=========================   -");
        Assertions.assertThat(Base64.decodeBase64("=========================   -")).isEmpty();
    }


    @Test
    void givenAByteFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        BinaryGenerator strategy = new BinaryGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("$#@$#@$#@*$@#$#@");
        Assertions.assertThat(Base64.decodeBase64("$#@$#@$#@*$@#$#@")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"byte,true", "binary,true", "not,false"})
    void shouldTestForApply(String format, boolean expected) {
        BinaryGenerator binaryGenerator = new BinaryGenerator();
        Assertions.assertThat(binaryGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

}
