package com.endava.cats.dsl.impl;


import com.endava.cats.exception.CatsException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

@QuarkusTest
class AuthScriptProviderParserTest {
    private AuthScriptProviderParser authScriptProviderParser;

    private PrettyLogger prettyLogger;

    @BeforeEach
    void setup() {
        authScriptProviderParser = new AuthScriptProviderParser();
        prettyLogger = Mockito.mock(PrettyLogger.class);
        ReflectionTestUtils.setField(authScriptProviderParser, "logger", prettyLogger);
    }

    @Test
    void shouldThrowIOExceptionWhenScriptNotProvided() {
        Map<?, ?> context = Map.of();
        Assertions.assertThatThrownBy(() -> authScriptProviderParser.parse(null, context)).isInstanceOf(CatsException.class);
    }

    @Test
    void shouldRunScriptButDontRefresh() {
        Map<String, String> context = Map.of("auth_script", "hostname");
        Assertions.assertThat(authScriptProviderParser.parse(null, context)).isNotBlank();
        authScriptProviderParser.parse(null, context);
        Mockito.verify(prettyLogger, Mockito.times(0)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(1)).info("Running script {} to get credentials", "hostname");
    }

    @Test
    void shouldRefreshOnInterval() throws Exception {
        Map<String, String> context = Map.of("auth_script", "hostname", "refresh_interval", "1");

        Assertions.assertThat(authScriptProviderParser.parse(null, context)).isNotBlank();
        Mockito.verify(prettyLogger, Mockito.times(0)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(1)).info("Running script {} to get credentials", "hostname");

        Thread.sleep(500);
        Assertions.assertThat(authScriptProviderParser.parse(null, context)).isNotBlank();
        Mockito.verify(prettyLogger, Mockito.times(0)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(1)).info("Running script {} to get credentials", "hostname");

        Thread.sleep(600);
        Assertions.assertThat(authScriptProviderParser.parse(null, context)).isNotBlank();
        Mockito.verify(prettyLogger, Mockito.times(1)).debug("Refresh interval passed.");
        Mockito.verify(prettyLogger, Mockito.times(2)).info("Running script {} to get credentials", "hostname");

    }
}
