package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.codec.binary.Base64;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ByteFormatGeneratorStrategyTest {

    @Test
    void givenAByteFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        ByteFormatGeneratorStrategy strategy = new ByteFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("=========================   -");
        Assertions.assertThat(Base64.decodeBase64("=========================   -")).isEmpty();
    }


    @Test
    void givenAByteFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        ByteFormatGeneratorStrategy strategy = new ByteFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("$#@$#@$#@*$@#$#@");
        Assertions.assertThat(Base64.decodeBase64("$#@$#@$#@*$@#$#@")).isEmpty();
    }
}
