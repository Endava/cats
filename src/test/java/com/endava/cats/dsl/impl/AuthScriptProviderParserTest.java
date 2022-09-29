package com.endava.cats.dsl.impl;


import com.endava.cats.args.AuthArguments;
import com.endava.cats.util.CatsException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class AuthScriptProviderParserTest {
    private AuthArguments authArguments;
    private AuthScriptProviderParser authScriptProviderParser;

    private PrettyLogger prettyLogger;

    @BeforeEach
    void setup() {
        authArguments = Mockito.mock(AuthArguments.class);
        authScriptProviderParser = new AuthScriptProviderParser(authArguments);
        prettyLogger = Mockito.mock(PrettyLogger.class);
        ReflectionTestUtils.setField(authScriptProviderParser, "logger", prettyLogger);
    }

    @Test
    void shouldThrowIOExceptionWhenScriptNotProvided() {
        Assertions.assertThatThrownBy(() -> authScriptProviderParser.parse(null, null)).isInstanceOf(CatsException.class);
    }

    @Test
    void shouldRunScriptButDontRefresh() {
        Mockito.when(authArguments.getAuthRefreshScript()).thenReturn("hostname");
        Assertions.assertThat(authScriptProviderParser.parse(null, null)).isNotBlank();
        authScriptProviderParser.parse(null, null);
        Mockito.verify(prettyLogger, Mockito.times(0)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(1)).info("Running script {} to get credentials", "hostname");
    }

    @Test
    void shouldRefreshOnInterval() throws Exception {
        Mockito.when(authArguments.getAuthRefreshScript()).thenReturn("hostname");
        Mockito.when(authArguments.getAuthRefreshInterval()).thenReturn(1);
        Assertions.assertThat(authScriptProviderParser.parse(null, null)).isNotBlank();
        Mockito.verify(prettyLogger, Mockito.times(0)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(1)).info("Running script {} to get credentials", "hostname");

        Thread.sleep(500);
        Assertions.assertThat(authScriptProviderParser.parse(null, null)).isNotBlank();
        Mockito.verify(prettyLogger, Mockito.times(0)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(1)).info("Running script {} to get credentials", "hostname");

        Thread.sleep(600);
        Assertions.assertThat(authScriptProviderParser.parse(null, null)).isNotBlank();
        Mockito.verify(prettyLogger, Mockito.times(1)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(2)).info("Running script {} to get credentials", "hostname");

    }
}
