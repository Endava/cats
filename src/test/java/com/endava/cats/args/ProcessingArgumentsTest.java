package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

@QuarkusTest
class ProcessingArgumentsTest {


    @Test
    void shouldMatchXxxSelectionWhenArgumentNotProvided() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        Assertions.assertThat(processingArguments.matchesXxxSelection("")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"address.line2,Local,true", "address.type,Local,true", "address.type,Global,false"})
    void shouldMatchXxxSelectionWhenArgumentProvidedButNotPresentInPayload(String field, String value, boolean expected) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        String payload = """
                {
                  "address": {
                    "type": "Local",
                    "line1": "my street"
                  }
                }
                """;
        processingArguments.xxxOfSelections = Map.of(field, value);
        Assertions.assertThat(processingArguments.matchesXxxSelection(payload)).isEqualTo(expected);
    }

    @Test
    void shouldReturnDefaultContentTypes() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        Assertions.assertThat(processingArguments.getContentType()).containsExactly("application\\/.*\\+?json;?.*", "application/x-www-form-urlencoded");
    }

    @Test
    void shouldReturnProvidedContentType() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "contentType", "app/xml");
        Assertions.assertThat(processingArguments.getContentType()).doesNotContain("application/json", "application/x-www-form-urlencoded").containsExactly("app/xml");
    }

    @Test
    void shouldReturnDefaultContentType() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        Assertions.assertThat(processingArguments.getDefaultContentType()).isEqualTo("application/json");
    }
}
