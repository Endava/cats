package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

@QuarkusTest
class CatsParamExceptionHandlerTest {

    @Test
    void shouldHandleException() {
        CatsParamExceptionHandler exceptionHandler = new CatsParamExceptionHandler();
        int exitCode = exceptionHandler.handleParseException(new CommandLine.ParameterException(Mockito.mock(CommandLine.class), "test"), null);
        Assertions.assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
    }
}
