package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.util.Collections;
import java.util.stream.Stream;

@QuarkusTest
class ApiArgumentsTest {

    @Test
    void shouldNotReturnRemoteContractWhenNull() {
        ApiArguments apiArguments = new ApiArguments();
        Assertions.assertThat(apiArguments.isRemoteContract()).isFalse();
    }

    @Test
    void shouldNotReturnRemoteContractWhenLocal() {
        ApiArguments apiArguments = new ApiArguments();
        ReflectionTestUtils.setField(apiArguments, "contract", "local");

        Assertions.assertThat(apiArguments.isRemoteContract()).isFalse();
    }

    @Test
    void shouldReturnRemoteContractWhenHttp() {
        ApiArguments apiArguments = new ApiArguments();
        ReflectionTestUtils.setField(apiArguments, "contract", "http://localhost/apu.yml");

        Assertions.assertThat(apiArguments.isRemoteContract()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenServerNotSupplied() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments apiArguments = new ApiArguments();
        ReflectionTestUtils.setField(apiArguments, "contract", "contract");
        Assertions.assertThatThrownBy(() -> apiArguments.validateValidServer(spec, null))
                .isInstanceOf(CommandLine.ParameterException.class).hasMessageContaining("server");
    }

    @Test
    void shouldThrowExceptionWhenContractNotSupplied() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments apiArguments = new ApiArguments();
        Assertions.assertThatThrownBy(() -> apiArguments.validateRequired(spec))
                .isInstanceOf(CommandLine.ParameterException.class).hasMessageContaining("contract");
    }

    @Test
    void shouldThrowExceptionWhenServerAndOpenApiNull() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments args = new ApiArguments();
        args.setServer(null);

        Assertions.assertThatThrownBy(() -> args.validateValidServer(spec, null))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("server");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serverResolutionArguments")
    void shouldResolveServerAccordingToPriority(String description, String initialServer, String openApiServerUrl, String expectedServer) {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments args = new ApiArguments();
        args.setServer(initialServer);

        OpenAPI openAPI = new OpenAPI();
        openAPI.setServers(Collections.singletonList(
                new io.swagger.v3.oas.models.servers.Server().url(openApiServerUrl)
        ));

        args.validateValidServer(spec, openAPI);

        Assertions.assertThat(args.getServer()).isEqualTo(expectedServer);
    }

    private static Stream<Arguments> serverResolutionArguments() {
        return Stream.of(
                Arguments.of("should set server from OpenAPI when CLI server is null", null, "http://fromopenapi.com", "http://fromopenapi.com"),
                Arguments.of("should replace OpenAPI placeholder with CLI server", "http://api.com", "{apiRoot}/v2", "http://api.com/v2"),
                Arguments.of("should prefer CLI server over concrete OpenAPI server", "http://localhost:8080", "https://api.example.com", "http://localhost:8080")
        );
    }

    @Test
    void shouldThrowExceptionWhenServerIsInvalidUrl() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments args = new ApiArguments();
        args.setServer("ftsp://invalid-url");

        Assertions.assertThatThrownBy(() -> args.validateValidServer(spec, null))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("valid <server> URL");
    }

    @Test
    void shouldPassWhenServerIsValidUrl() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments args = new ApiArguments();
        args.setServer("http://valid-url.com");

        Assertions.assertThatCode(() -> args.validateValidServer(spec, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRemoveTrailingSlashFromServer() {
        ApiArguments args = new ApiArguments();
        args.setServer("http://api.com/");
        args.validateValidServer(null, null);
        Assertions.assertThat(args.getServer()).isEqualTo("http://api.com");
    }
}
