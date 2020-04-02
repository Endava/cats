package com.endava.cats.generator.format.impl;

import org.apache.commons.codec.binary.Base64;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ByteFormatGeneratorStrategyTest {

    @Test
    public void givenAByteFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        ByteFormatGeneratorStrategy strategy = new ByteFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("YmFzZTY0IGRlY29kZX==-");
        Assertions.assertThatThrownBy(() -> Base64.decodeBase64("YmFzZTY0IGRlY29kZX==-")).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void givenAByteFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        ByteFormatGeneratorStrategy strategy = new ByteFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("a2=========================   -");
        Assertions.assertThatThrownBy(() -> Base64.decodeBase64("a2=========================   -")).isInstanceOf(IllegalArgumentException.class);
    }
}
