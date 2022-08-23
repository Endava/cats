package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class ProcessingArgumentsTest {

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
