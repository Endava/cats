package com.endava.cats.model.generator.impl;


import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class URIGeneratorTest {

    private URIGenerator uriGenerator;

    @BeforeEach
    void setup() {
        uriGenerator = new URIGenerator();
    }

    @ParameterizedTest
    @CsvSource({"not,url,true", "not,uri,true", "not,addressurl,true", "not,addressuri,true", "not,not,false", "url,not,true", "uri,not,true"})
    void shouldRecognizeUrl(String format, String property, boolean expected) {
        boolean isIp = uriGenerator.appliesTo(format, property);
        Assertions.assertThat(isIp).isEqualTo(expected);
    }
}

