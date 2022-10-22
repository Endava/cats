package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class EmailGeneratorTest {

    private EmailGenerator emailGenerator;

    @BeforeEach
    void setup() {
        emailGenerator = new EmailGenerator();
    }

    @ParameterizedTest
    @CsvSource({"not,email,true", "not,emailAddress,true", "not,notEmail,true", "not,myEmailAddress,true", "email,randomField,true", "not,randomField,false"})
    void shouldRecognizeEmail(String format, String property, boolean expected) {
        boolean isEmail = emailGenerator.appliesTo(format, property);
        Assertions.assertThat(isEmail).isEqualTo(expected);
    }
}
