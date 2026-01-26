package com.endava.cats.util.external;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class MediaTypeTest {

    @Test
    void shouldCreateSimpleMediaType() {
        MediaType mediaType = MediaType.create("application", "json");

        assertThat(mediaType.type()).isEqualTo("application");
        assertThat(mediaType.subtype()).isEqualTo("json");
        assertThat(mediaType.parameters()).isEmpty();
        assertThat(mediaType.hasWildcard()).isFalse();
    }

    @Test
    void shouldParseSimpleMediaType() {
        MediaType mediaType = MediaType.parse("application/json");

        assertThat(mediaType.type()).isEqualTo("application");
        assertThat(mediaType.subtype()).isEqualTo("json");
        assertThat(mediaType.parameters()).isEmpty();
    }

    @Test
    void shouldParseMediaTypeWithCharset() {
        MediaType mediaType = MediaType.parse("application/json; charset=UTF-8");

        assertThat(mediaType.type()).isEqualTo("application");
        assertThat(mediaType.subtype()).isEqualTo("json");
        assertThat(mediaType.parameters()).containsEntry("charset", "utf-8");
        assertThat(mediaType.charset()).contains(StandardCharsets.UTF_8);
    }

    @Test
    void shouldParseMediaTypeWithQuotedCharset() {
        MediaType mediaType = MediaType.parse("text/plain; charset=\"utf-8\"");

        assertThat(mediaType.type()).isEqualTo("text");
        assertThat(mediaType.subtype()).isEqualTo("plain");
        assertThat(mediaType.parameters()).containsEntry("charset", "utf-8");
    }

    @Test
    void shouldParseMediaTypeWithMultipleParameters() {
        MediaType mediaType = MediaType.parse("text/plain; charset=utf-8; format=flowed");

        assertThat(mediaType.type()).isEqualTo("text");
        assertThat(mediaType.subtype()).isEqualTo("plain");
        assertThat(mediaType.parameters()).containsEntry("charset", "utf-8");
        assertThat(mediaType.parameters()).containsEntry("format", "flowed");
    }

    @Test
    void shouldNormalizeTypeAndSubtype() {
        MediaType mediaType = MediaType.parse("APPLICATION/JSON");

        assertThat(mediaType.type()).isEqualTo("application");
        assertThat(mediaType.subtype()).isEqualTo("json");
    }

    @Test
    void shouldDetectWildcardType() {
        MediaType mediaType = MediaType.parse("*/*");

        assertThat(mediaType.hasWildcard()).isTrue();
        assertThat(mediaType.type()).isEqualTo("*");
        assertThat(mediaType.subtype()).isEqualTo("*");
    }

    @Test
    void shouldDetectWildcardSubtype() {
        MediaType mediaType = MediaType.parse("text/*");

        assertThat(mediaType.hasWildcard()).isTrue();
    }

    @Test
    void shouldAddCharset() {
        MediaType mediaType = MediaType.create("application", "json")
                .withCharset(StandardCharsets.UTF_8);

        assertThat(mediaType.charset()).contains(StandardCharsets.UTF_8);
    }

    @Test
    void shouldAddParameter() {
        MediaType mediaType = MediaType.create("text", "plain")
                .withParameter("format", "flowed");

        assertThat(mediaType.parameters()).containsEntry("format", "flowed");
    }

    @Test
    void shouldRemoveParameters() {
        MediaType mediaType = MediaType.parse("text/plain; charset=utf-8")
                .withoutParameters();

        assertThat(mediaType.parameters()).isEmpty();
    }

    @Test
    void shouldReturnSameInstanceWhenRemovingParametersFromEmptyMediaType() {
        MediaType mediaType = MediaType.create("application", "json");
        MediaType result = mediaType.withoutParameters();

        assertThat(result).isSameAs(mediaType);
    }

    @ParameterizedTest
    @CsvSource({"application/json", "application/json; charset=invalid", "text/plain; charset="})
    void shouldReturnEmptyCharset(String mediaTypeText) {
        MediaType mediaType = MediaType.parse(mediaTypeText);

        assertThat(mediaType.charset()).isEmpty();
    }


    @Test
    void shouldConvertToString() {
        MediaType mediaType = MediaType.create("application", "json")
                .withCharset(StandardCharsets.UTF_8);

        assertThat(mediaType).hasToString("application/json; charset=utf-8");
    }

    @Test
    void shouldQuoteParameterWithSpaces() {
        MediaType mediaType = MediaType.create("text", "plain")
                .withParameter("boundary", "simple boundary");

        assertThat(mediaType.toString()).contains("boundary=\"simple boundary\"");
    }

    @Test
    void shouldEqualsSameMediaType() {
        MediaType mt1 = MediaType.parse("application/json; charset=utf-8");
        MediaType mt2 = MediaType.parse("application/json; charset=utf-8");

        assertThat(mt1).isEqualTo(mt2).hasSameHashCodeAs(mt2);
    }

    @Test
    void shouldNotEqualsDifferentType() {
        MediaType mt1 = MediaType.parse("application/json");
        MediaType mt2 = MediaType.parse("text/plain");

        assertThat(mt1).isNotEqualTo(mt2);
    }

    @Test
    void shouldEqualsSelf() {
        MediaType mediaType = MediaType.parse("application/json");

        assertThat(mediaType.equals(mediaType)).isTrue();
    }

    @Test
    void shouldNotEqualsNull() {
        MediaType mediaType = MediaType.parse("application/json");

        assertThat(mediaType).isNotEqualTo(null);
    }

    @Test
    void shouldThrowExceptionForEmptyInput() {
        assertThatThrownBy(() -> MediaType.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThatThrownBy(() -> MediaType.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowExceptionForMissingSlash() {
        assertThatThrownBy(() -> MediaType.parse("applicationjson"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMatchWithWildcard() {
        MediaType anyType = MediaType.ANY_TYPE;
        MediaType specific = MediaType.parse("application/json");

        assertThat(specific.is(anyType)).isTrue();
    }

    @Test
    void shouldMatchExactType() {
        MediaType mt1 = MediaType.parse("application/json");
        MediaType mt2 = MediaType.parse("application/json");

        assertThat(mt1.is(mt2)).isTrue();
    }

    @Test
    void shouldNotMatchDifferentType() {
        MediaType json = MediaType.parse("application/json");
        MediaType xml = MediaType.parse("application/xml");

        assertThat(json.is(xml)).isFalse();
    }

    @Test
    void shouldHaveConstants() {
        assertThat(MediaType.JSON_UTF_8.type()).isEqualTo("application");
        assertThat(MediaType.PLAIN_TEXT_UTF_8.type()).isEqualTo("text");
        assertThat(MediaType.ANY_TYPE.type()).isEqualTo("*");
    }

    @Test
    void shouldThrowExceptionForNullCharset() {
        MediaType mediaType = MediaType.create("application", "json");

        assertThatThrownBy(() -> mediaType.withCharset(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowExceptionForNullParameter() {
        MediaType mediaType = MediaType.create("application", "json");

        assertThatThrownBy(() -> mediaType.withParameter(null, "value"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleWhitespace() {
        MediaType mediaType = MediaType.parse("  application/json  ;  charset=utf-8  ");

        assertThat(mediaType.type()).isEqualTo("application");
        assertThat(mediaType.subtype()).isEqualTo("json");
    }

    @Test
    void shouldIgnoreInvalidParameters() {
        MediaType mediaType = MediaType.parse("text/plain; invalid; charset=utf-8");

        assertThat(mediaType.parameters()).containsEntry("charset", "utf-8");
    }

    @Test
    void shouldMatchWithSubsetParameters() {
        MediaType withCharset = MediaType.parse("text/plain; charset=utf-8");
        MediaType withoutParams = MediaType.parse("text/plain");

        assertThat(withCharset.is(withoutParams)).isTrue();
    }

    @Test
    void shouldNotMatchDifferentParameters() {
        MediaType mt1 = MediaType.parse("text/plain; charset=utf-8");
        MediaType mt2 = MediaType.parse("text/plain; charset=iso-8859-1");

        assertThat(mt1.is(mt2)).isFalse();
    }
}
