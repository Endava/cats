package com.endava.cats.report;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class ProblemPageTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "/risk-rules/{ruleId}|/<wbr>risk-rules/<wbr><span class=\"param-tok\">{ruleId}</span>",
            "/a<b>&'c\"|/<wbr>a&lt;b&gt;&amp;&#39;c&quot;",
            "NULL|''"
    })
    void shouldRenderTablePathsOnTheServer(String path, String expected) {
        Assertions.assertThat(ProblemPage.TestRow.pathHtml(path)).isEqualTo(expected);
    }

    @Test
    void shouldNotRepeatTheServerMessageInTheCardHeading() {
        ProblemCard message = card("Request rejected (HTTP 422): Field is required", "Field is required");
        ProblemCard accepted = card("XSS payload reflected in response (HTTP 2xx)", null);

        Assertions.assertThat(message.heading()).isEqualTo("Request rejected (HTTP 422)");
        Assertions.assertThat(accepted.heading()).isEqualTo("XSS payload reflected in response (HTTP 2xx)");
    }

    private static ProblemCard card(String title, String serverMessage) {
        return new ProblemCard(1, "1a2b3c4d", "problem-1a2b3c4d", "ERROR_MESSAGE", "Rejected", "error", title, "", serverMessage,
                null, null, "", List.of(), null, List.of(), List.of(), null, 1);
    }
}
