package com.endava.cats.args;

import com.endava.cats.util.SensitiveDataPolicy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.config.level.PrettyLevel;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class ReportingArgumentsTest {
    @Test
    void shouldEnableTerminalInterfaceFromCommandLine() {
        ReportingArguments arguments = new ReportingArguments();

        new CommandLine(arguments).parseArgs("--tui", "--tuiMaxResults", "250");

        Assertions.assertThat(arguments.isTui()).isTrue();
        Assertions.assertThat(arguments.getTuiMaxResults()).isEqualTo(250);
    }

    @Test
    void shouldAutomaticallyMaskSensitiveHeaders() {
        ReportingArguments arguments = new ReportingArguments();

        Assertions.assertThat(arguments.shouldMaskHeader("Authorization")).isTrue();
        Assertions.assertThat(arguments.shouldMaskHeader("X-API-Key")).isTrue();
        Assertions.assertThat(arguments.shouldMaskHeader("Accept")).isFalse();
    }

    @Test
    void shouldOnlyAutomaticallyMaskExactSensitiveQueryNames() {
        ReportingArguments arguments = new ReportingArguments();

        Assertions.assertThat(arguments.shouldMaskQueryParam("access_token")).isTrue();
        Assertions.assertThat(arguments.shouldMaskQueryParam("allowed_token_usage")).isFalse();
    }

    @Test
    void shouldMaskQueryNameRegisteredByAuthenticationMetadata() {
        ReportingArguments arguments = new ReportingArguments();
        arguments.registerSensitiveQueryParams(Set.of("allowed_token_usage"));

        Assertions.assertThat(arguments.shouldMaskQueryParam("allowed_token_usage")).isTrue();
    }

    @Test
    void shouldFindQueryApiKeysDeclaredByOpenApi() {
        SecurityScheme queryKey = new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.QUERY).name("custom_auth");
        SecurityScheme headerKey = new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER).name("X-API-Key");
        OpenAPI openAPI = new OpenAPI().components(new Components().securitySchemes(
                Map.of("query", queryKey, "header", headerKey)));

        Assertions.assertThat(SensitiveDataPolicy.openApiQueryApiKeyNames(openAPI)).containsExactly("custom_auth");
    }

    @Test
    void shouldKeepExplicitMasksWhenShowingAutomaticSecrets() {
        ReportingArguments arguments = new ReportingArguments();
        ReflectionTestUtils.setField(arguments, "showSecrets", true);
        ReflectionTestUtils.setField(arguments, "maskHeaders", Set.of("X-Custom"));

        Assertions.assertThat(arguments.shouldMaskHeader("Authorization")).isFalse();
        Assertions.assertThat(arguments.shouldMaskHeader("x-custom")).isTrue();
    }

    @Test
    void shouldRestoreConfiguredLevelsAfterTui() {
        ReportingArguments arguments = new ReportingArguments();
        new CommandLine(arguments).parseArgs("--tui", "--verbosity", "DETAILED", "--onlyLog", "error,success");

        try {
            arguments.processLogData();
            arguments.restoreLogDataAfterTui();

            @SuppressWarnings("unchecked")
            Map<String, Boolean> levels = (Map<String, Boolean>) ReflectionTestUtils.getField(PrettyLogger.class, "LEVELS_MAP");
            Assertions.assertThat(levels).containsEntry("ERROR", true).containsEntry("SUCCESS", true)
                    .containsEntry("INFO", false);
        } finally {
            PrettyLogger.enableLevels(PrettyLevel.values());
        }
    }
}
