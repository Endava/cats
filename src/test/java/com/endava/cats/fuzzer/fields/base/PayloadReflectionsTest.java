package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

@QuarkusTest
class PayloadReflectionsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "{ $where: function() { return true; } }|{\"name\":\"{ $where: function() { return true; } }\",\"_id\":\"1\"}",
            "{\"$gt\": \"\"}|{\"name\":\"{\\\"$gt\\\": \\\"\\\"}\",\"_id\":\"1\"}",
            "' OR '1'='1|{\"name\":\"\\u0027 OR \\u00271\\u0027\\u003d\\u00271\",\"_id\":\"1\"}",
            "<script>alert(1)</script>|<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>",
            "http://169.254.169.254/latest/dynamic/instance-identity/document|{\"callback\":\"http:\\/\\/169.254.169.254\\/latest\\/dynamic\\/instance-identity\\/document\"}",
            "../../etc/passwd|redirect?file=..%2F..%2Fetc%2Fpasswd",
            "; LS -LA|value '; ls -la' is invalid"
    })
    void shouldStripRawAndEncodedReflectionsIgnoringCase(String payload, String body) {
        CatsResponse stripped = PayloadReflections.strip(response(body), payload);

        Assertions.assertThat(stripped.getBody().toLowerCase(Locale.ROOT))
                .doesNotContain("$where", "\\\"$gt\\\"", "\\u0027 or", "&lt;script", "instance-identity", "etc%2fpasswd", "ls -la");
    }

    @Test
    void shouldKeepEvidenceProducedOutsideTheReflection() {
        CatsResponse stripped = PayloadReflections.strip(response("$(echo vulnerable) => vulnerable"), "$(echo vulnerable)");

        Assertions.assertThat(stripped.getBody()).isEqualTo(" => vulnerable");
    }

    @Test
    void shouldPreserveResponseMetadataWhenStripping() {
        CatsResponse original = CatsResponse.builder().responseCode(400).responseContentType("application/json")
                .body("{\"error\":\"bad value '; ls -la'\"}").build();

        CatsResponse stripped = PayloadReflections.strip(original, "; ls -la");

        Assertions.assertThat(stripped.getResponseCode()).isEqualTo(400);
        Assertions.assertThat(stripped.getResponseContentType()).isEqualTo("application/json");
        Assertions.assertThat(stripped.getBody()).isEqualTo("{\"error\":\"bad value ''\"}");
    }

    @Test
    void shouldReturnSameResponseWhenNothingIsReflected() {
        CatsResponse original = response("{\"status\":\"ok\"}");

        Assertions.assertThat(PayloadReflections.strip(original, "' OR '1'='1")).isSameAs(original);
        Assertions.assertThat(PayloadReflections.strip(original, "")).isSameAs(original);
        Assertions.assertThat(PayloadReflections.strip(response(null), "x").getBody()).isNull();
    }

    private static CatsResponse response(String body) {
        return CatsResponse.builder().responseCode(200).body(body).build();
    }
}
