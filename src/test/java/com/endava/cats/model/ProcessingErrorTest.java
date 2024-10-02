package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessingErrorTest {

    @Test
    void shouldIncludePath() {
        ProcessingError error = new ProcessingError("path", "GET", "message");
        String result = error.toString();
        Assertions.assertThat(result).isEqualTo("Path path, http method GET: message");
    }

    @Test
    void shouldNotIncludePath() {
        ProcessingError error = new ProcessingError(null, "GET", "message");
        String result = error.toString();
        Assertions.assertThat(result).isEqualTo("http method GET: message");
    }

    @Test
    void shouldNotIncludeHttpMethod() {
        ProcessingError error = new ProcessingError("path", null, "message");
        String result = error.toString();
        Assertions.assertThat(result).isEqualTo("Path path, message");
    }
}
