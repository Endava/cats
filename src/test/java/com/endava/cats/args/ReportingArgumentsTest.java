package com.endava.cats.args;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
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

import java.util.List;
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

    @Test
    void shouldRejectInvalidReportFormat() {
        ReportingArguments reportingArguments = new ReportingArguments();
        CommandLine commandLine = new CommandLine(reportingArguments).setCaseInsensitiveEnumValuesAllowed(true);

        Assertions.assertThatThrownBy(() -> commandLine.parseArgs("--reportFormat", "PDF"))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("expected one of [HTML_ONLY, HTML_JS, JUNIT, WFC_REPORT]");
    }

    @Test
    void shouldParseReportFormatsCaseInsensitively() {
        ReportingArguments reportingArguments = new ReportingArguments();
        new CommandLine(reportingArguments).setCaseInsensitiveEnumValuesAllowed(true)
                .parseArgs("--reportFormat", "html_only,WFC_REPORT");

        Assertions.assertThat(reportingArguments.getReportFormat())
                .containsExactly(ReportingArguments.ReportFormat.HTML_ONLY, ReportingArguments.ReportFormat.WFC_REPORT);
    }

    @Test
    void shouldMaskConfiguredRequestAndResponseFieldsWhilePreservingFuzzedMutations() {
        ReportingArguments arguments = new ReportingArguments();
        new CommandLine(arguments).parseArgs("--mask", "password,auth.clientSecret");
        arguments.registerSensitiveField("apiKey", "SERVICE_API_KEY", "real-api-key-val");

        CatsTestCase baseline = new CatsTestCase();
        baseline.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .url("https://service.test/login?password=secretQuery")
                .payload("{\"username\":\"alice\",\"password\":\"s3cr3t\",\"apiKey\":\"real-api-key-val\",\"auth\":{\"clientSecret\":[\"top-secret-1\",\"top-secret-2\"]}}")
                .encodedBody("base64-raw")
                .build());
        baseline.setResponse(CatsResponse.builder()
                .responseCode(200)
                .body("{\"password\":\"echoed\",\"auth\":{\"clientSecret\":[\"echoed-secret\"]},\"status\":\"ok\"}")
                .build());

        SensitiveDataPolicy.sanitize(baseline, arguments);

        Assertions.assertThat(baseline.getRequest().getPayload())
                .contains("\"password\":\"$$password\"")
                .contains("\"apiKey\":\"$$SERVICE_API_KEY\"")
                .contains("\"clientSecret\":[\"$$auth_clientSecret\",\"$$auth_clientSecret\"]")
                .doesNotContain("s3cr3t", "real-api-key-val", "top-secret-1", "top-secret-2");
        Assertions.assertThat(baseline.getRequest().getEncodedBody()).isNull();
        Assertions.assertThat(baseline.getRequest().getUrl()).contains("password=$$PASSWORD");
        Assertions.assertThat(baseline.getResponse().getBody())
                .contains(SensitiveDataPolicy.REDACTED)
                .doesNotContain("echoed", "echoed-secret");
        Assertions.assertThat(arguments.getReplayEnvironmentVariables())
                .contains("password", "SERVICE_API_KEY", "auth_clientSecret", "PASSWORD");

        CatsTestCase mutated = new CatsTestCase();
        mutated.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .url("https://service.test/login")
                .payload("{\"password\":\"' OR 1=1--\",\"apiKey\":\"replaced-by-fuzzer\",\"nested\":{\"password\":\"still-secret\"}}")
                .build());
        mutated.getRequestProvenance().addMutationTargets(List.of(RequestTarget.body("password"), RequestTarget.body("apiKey")));

        SensitiveDataPolicy.sanitize(mutated, arguments);

        Assertions.assertThat(mutated.getRequest().getPayload())
                .contains("\"password\":\"' OR 1=1--\"")
                .contains("\"apiKey\":\"replaced-by-fuzzer\"")
                .contains("\"nested\":{\"password\":\"$$password\"}")
                .doesNotContain("still-secret");
    }

    @Test
    void shouldMaskFormUrlEncodedRequestFields() {
        ReportingArguments arguments = new ReportingArguments();
        new CommandLine(arguments).parseArgs("--maskFields", "client_secret,auth.clientSecret");
        arguments.registerSensitiveField("runtimeSecret", "FORM_SECRET", "my secret&1");

        String masked = SensitiveDataPolicy.maskPayloadForDisplay(
                "grant_type=client_credentials&client_secret=mySecret123&auth%5BclientSecret%5D=nested%20secret&runtimeSecret=my+secret%261",
                arguments);

        Assertions.assertThat(masked).isEqualTo(
                "grant_type=client_credentials&client_secret=$$client_secret&auth%5BclientSecret%5D=$$auth_clientSecret&runtimeSecret=$$FORM_SECRET");
    }

    @Test
    void shouldSeparateMaskFieldsFromMaskHeadersAndTrimWhitespace() {
        ReportingArguments fieldOnly = new ReportingArguments();
        new CommandLine(fieldOnly).parseArgs("--maskFields", " content-type , date , password ");

        Assertions.assertThat(fieldOnly.shouldMaskHeader("Content-Type")).isFalse();
        Assertions.assertThat(fieldOnly.shouldMaskHeader("Date")).isFalse();
        Assertions.assertThat(fieldOnly.shouldMaskQueryParam("date")).isFalse();
        Assertions.assertThat(SensitiveDataPolicy.maskPayloadForDisplay("{\"password\":\"s3cr3t\",\"date\":\"2026-01-01\"}", fieldOnly))
                .contains("\"password\":\"$$password\"", "\"date\":\"$$date\"")
                .doesNotContain("s3cr3t", "2026-01-01");

        ReportingArguments headerOnly = new ReportingArguments();
        new CommandLine(headerOnly).parseArgs("--maskHeaders", " X-Custom ");

        Assertions.assertThat(headerOnly.shouldMaskHeader("X-Custom")).isTrue();
        Assertions.assertThat(SensitiveDataPolicy.maskPayloadForDisplay("{\"X-Custom\":\"keep\"}", headerOnly))
                .contains("\"X-Custom\":\"keep\"");

        ReportingArguments unified = new ReportingArguments();
        new CommandLine(unified).parseArgs("--mask", " X-Custom , date ");

        Assertions.assertThat(unified.shouldMaskHeader("X-Custom")).isTrue();
        Assertions.assertThat(unified.shouldMaskQueryParam("date")).isTrue();
    }

    @Test
    void shouldMaskNestedArraysParentObjectsAndArrayPathNotations() {
        ReportingArguments arguments = new ReportingArguments();
        new CommandLine(arguments).parseArgs("--maskFields", "tokens,credentials,secrets,items[*].password,$.auth.secret");

        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .url("https://service.test/bulk")
                .payload("{\"tokens\":[[\"tok1\",\"tok2\"]],\"credentials\":{\"user\":\"alice\",\"pass\":\"p1\"},\"secrets\":[{\"val\":\"v1\"}],\"items\":[{\"password\":\"item-pwd\"}],\"auth\":{\"secret\":\"a-sec\"}}")
                .build());
        testCase.setResponse(CatsResponse.builder()
                .responseCode(200)
                .body("{\"tokens\":[[\"tok1\"]],\"credentials\":{\"pass\":\"p1\"}}")
                .build());

        SensitiveDataPolicy.sanitize(testCase, arguments);

        Assertions.assertThat(testCase.getRequest().getPayload())
                .contains("\"tokens\":[[\"$$tokens\",\"$$tokens\"]]")
                .contains("\"user\":\"$$credentials_user\"")
                .contains("\"pass\":\"$$credentials_pass\"")
                .contains("\"val\":\"$$secrets_val\"")
                .contains("\"password\":\"$$items_password\"")
                .contains("\"secret\":\"$$auth_secret\"")
                .doesNotContain("tok1", "tok2", "alice", "p1", "v1", "item-pwd", "a-sec");
        Assertions.assertThat(testCase.getResponse().getBody())
                .isEqualTo("{\"tokens\":[[\"[REDACTED]\"]],\"credentials\":{\"pass\":\"[REDACTED]\"}}");

        CatsTestCase mutatedArrayItem = new CatsTestCase();
        mutatedArrayItem.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .url("https://service.test/bulk")
                .payload("{\"items\":[{\"password\":\"' OR 1=1--\"}]}")
                .build());
        mutatedArrayItem.getRequestProvenance().addMutationTargets(List.of(RequestTarget.body("items[*]#password")));

        SensitiveDataPolicy.sanitize(mutatedArrayItem, arguments);

        Assertions.assertThat(mutatedArrayItem.getRequest().getPayload())
                .contains("\"password\":\"' OR 1=1--\"");
    }

    @Test
    void shouldRetainMultipleRuntimeSecretsPreserveEmbeddedTemplatesAndNotClobberShortMutatedValues() {
        ReportingArguments arguments = new ReportingArguments();
        arguments.registerSensitiveField("password", "ADMIN_PWD", "admin-secret");
        arguments.registerSensitiveField("password", "GUEST_PWD", "guest-secret");
        arguments.registerSensitiveField("token", "API_TOKEN", "Bearer real-tok", "Bearer $$API_TOKEN");
        arguments.registerSensitiveField("creds", "USER_ID", "alice:s3cr3t", "$$USER_ID:$$USER_PASS");
        arguments.registerSensitiveField("pin", "PIN_CODE", "1");

        CatsTestCase adminCase = new CatsTestCase();
        adminCase.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .url("https://service.test/admin")
                .payload("{\"password\":\"admin-secret\",\"token\":\"Bearer real-tok\",\"creds\":\"alice:s3cr3t\"}")
                .build());

        SensitiveDataPolicy.sanitize(adminCase, arguments);
        SensitiveDataPolicy.sanitize(adminCase, arguments);

        Assertions.assertThat(adminCase.getRequest().getPayload())
                .contains("\"password\":\"$$ADMIN_PWD\"")
                .contains("\"token\":\"Bearer $$API_TOKEN\"")
                .contains("\"creds\":\"$$USER_ID:$$USER_PASS\"")
                .doesNotContain("admin-secret", "real-tok", "alice:s3cr3t");
        Assertions.assertThat(arguments.getReplayEnvironmentVariables())
                .contains("ADMIN_PWD", "API_TOKEN", "USER_ID", "USER_PASS");

        CatsTestCase mutatedShort = new CatsTestCase();
        mutatedShort.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .url("https://service.test/pin")
                .payload("{\"pin\":\"' OR 1=1--\"}")
                .build());
        mutatedShort.getRequestProvenance().addMutationTargets(List.of(RequestTarget.body("pin")));

        SensitiveDataPolicy.sanitize(mutatedShort, arguments);

        Assertions.assertThat(mutatedShort.getRequest().getPayload())
                .contains("\"pin\":\"' OR 1=1--\"");
    }

    @Test
    void shouldNotOverMaskUnrelatedResponseValuesForRuntimeEnvFieldsAndShouldPreserveMutatedQueryParams() {
        ReportingArguments arguments = new ReportingArguments();
        new CommandLine(arguments).parseArgs("--mask", "customParam");
        arguments.registerSensitiveField("id", "SEED_ID", "seed-secret-999");

        CatsTestCase unrelatedResponse = new CatsTestCase();
        unrelatedResponse.setRequest(CatsRequest.builder()
                .httpMethod("GET")
                .url("https://service.test/orders?token=fuzzed-sql-probe&customParam=keep-secret&access_token=real-tok")
                .build());
        unrelatedResponse.getRequestProvenance().addMutationTargets(List.of(RequestTarget.query("token")));
        unrelatedResponse.setResponse(CatsResponse.builder()
                .responseCode(200)
                .body("{\"id\":42,\"echoed\":{\"id\":\"seed-secret-999\"}}")
                .build());

        SensitiveDataPolicy.sanitize(unrelatedResponse, arguments);

        Assertions.assertThat(unrelatedResponse.getRequest().getUrl())
                .contains("token=fuzzed-sql-probe")
                .contains("customParam=$$CUSTOMPARAM")
                .contains("access_token=$$ACCESS_TOKEN")
                .doesNotContain("keep-secret", "real-tok");
        Assertions.assertThat(unrelatedResponse.getResponse().getBody())
                .isEqualTo("{\"id\":42,\"echoed\":{\"id\":\"[REDACTED]\"}}");
    }
}
