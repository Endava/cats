package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

@QuarkusTest
class UserArgumentsTest {
    private UserArguments userArguments;

    @BeforeEach
    void setup() {
        userArguments = new UserArguments();
    }

    @Test
    void shouldThrowExceptionWhenFileNotFound() {
        userArguments.words = new File("muhaha");
        Assertions.assertThrows(RuntimeException.class, () -> userArguments.getWordsAsList());
    }

    @Test
    void shouReturnFileContents() {
        userArguments.words = new File("src/test/resources/headers.yml");
        org.assertj.core.api.Assertions.assertThat(userArguments.getWordsAsList()).contains("auth-header:");
    }
}
