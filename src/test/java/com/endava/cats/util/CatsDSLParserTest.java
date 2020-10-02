package com.endava.cats.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class CatsDSLParserTest {

    private CatsDSLParser catsDSLParser;

    @BeforeEach
    public void setup() {
        catsDSLParser = new CatsDSLParser();
    }

    @Test
    void shouldReturnSameValue() {
        String initial = "test";
        String actual = catsDSLParser.parseAndGetResult(initial);

        Assertions.assertThat(actual).isEqualTo(initial);
    }

    @Test
    void shouldParseAsDate() {
        String initial = "T(java.time.OffsetDateTime).now().plusDays(2)";
        String actual = catsDSLParser.parseAndGetResult(initial);
        OffsetDateTime actualDate = OffsetDateTime.parse(actual);
        Assertions.assertThat(actualDate).isAfter(OffsetDateTime.now().plusDays(1));
    }

    @Test
    void shouldIgnoreAsMethodInvalid() {
        String initial = "T(java.time.OffsetDateTime).nowMe().plusDays(2)";
        String actual = catsDSLParser.parseAndGetResult(initial);

        Assertions.assertThat(actual).isEqualTo(initial);
    }
}
