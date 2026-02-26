package com.endava.cats.dsl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@QuarkusTest
class CatsDSLParserTest {

    @BeforeAll
    static void init() {
        CatsRandom.initRandom(42L);
    }

    public static final String JSON = """
            {
                "filed1": "113",
                "expiry": "2018-06-25",
                "match": "NOT"
              }""";

    @Test
    void shouldReturnSameValue() {
        String initial = "test";
        String actual = CatsDSLParser.parseAndGetResult(initial, Map.of());

        Assertions.assertThat(actual).isEqualTo(initial);
    }

    @Test
    void shouldReturnNullWhenValueIsNull() {
        Assertions.assertThat(CatsDSLParser.parseAndGetResult(null, Map.of())).isNull();
    }

    @Test
    void shouldParseAsDate() {
        String initial = "T(java.time.OffsetDateTime).now().plusDays(2)";
        String actual = CatsDSLParser.parseAndGetResult(initial, Map.of());
        OffsetDateTime actualDate = OffsetDateTime.parse(actual);
        Assertions.assertThat(actualDate).isAfter(OffsetDateTime.now(ZoneId.systemDefault()).plusDays(1));
    }

    @Test
    void shouldIgnoreAsMethodInvalid() {
        String initial = "T(java.time.OffsetDateTime).nowMe().plusDays(2)";
        String actual = CatsDSLParser.parseAndGetResult(initial, Map.of());

        Assertions.assertThat(actual).isEqualTo(initial);
    }

    @CsvSource({"request,${request.expiry}", "response,expiry"})
    @ParameterizedTest
    void shouldParseFromRequest(String context, String expression) {
        String initial = "T(java.time.LocalDate).now().isAfter(T(java.time.LocalDate).parse(" + expression + "))";
        String actual = CatsDSLParser.parseAndGetResult(initial, Map.of(context, JSON));

        Assertions.assertThat(actual).isEqualTo("true");
    }

    @Test
    void shouldParseValueFromOutputVariables() {
        String initial = "T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(${expiry}.toString()))";
        String actual = CatsDSLParser.parseAndGetResult(initial, Map.of("expiry", "2018-06-25"));
        Assertions.assertThat(actual).isEqualTo("false");
    }

    @Test
    void shouldSubstring() {
        String expression = "${name}.substring(1,3)";
        String actual = CatsDSLParser.parseAndGetResult(expression, Map.of("name", "john"));
        Assertions.assertThat(actual).isEqualTo("oh");
    }

    @Test
    void shouldGetSystemVariable() {
        String actual = CatsDSLParser.parseAndGetResult("$$PATH", null);
        Assertions.assertThat(actual).isNotBlank();
    }

    @Test
    void shouldGetNotFoundSystemVariable() {
        String actual = CatsDSLParser.parseAndGetResult("$$cats", null);
        Assertions.assertThat(actual).isEqualTo("not_found_$$cats");
    }

    @ParameterizedTest
    @CsvSource({"2023-02-02,2023-02-02", "${variable}-02-02,2023-02-02"})
    void shouldParseDateStringAsDate(String input, String parsedOutput) {
        String actual = CatsDSLParser.parseAndGetResult(input, Map.of("name", "john", "variable", "2023"));
        Assertions.assertThat(actual).isEqualTo(parsedOutput);
    }

    @Test
    void shouldParseShorthandNumeric() {
        String actual = CatsDSLParser.parseAndGetResult("#(numeric(16))", Map.of());
        Assertions.assertThat(actual).hasSize(16).matches("[0-9]+");
    }

    @Test
    void shouldParseShorthandUuid() {
        String actual = CatsDSLParser.parseAndGetResult("#(uuid)", Map.of());
        Assertions.assertThat(actual).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldParseShorthandEmail() {
        String actual = CatsDSLParser.parseAndGetResult("#(email)", Map.of());
        Assertions.assertThat(actual).matches("[a-z]{10}@cats\\.io");
    }
}
