package com.endava.cats.exception;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CatsExecutionCancelledExceptionTest {
    @Test
    void shouldStopAtCheckpointWhenThreadIsInterrupted() {
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThatThrownBy(CatsExecutionCancelledException::check)
                    .isInstanceOf(CatsExecutionCancelledException.class)
                    .hasMessage("Execution cancelled by user");
        } finally {
            Thread.interrupted();
        }
    }
}
