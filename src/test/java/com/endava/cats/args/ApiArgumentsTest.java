package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.util.Collections;

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

    @Test
    void shouldSetServerFromOpenApiWhenServerIsNull() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments args = new ApiArguments();
        args.setServer(null);

        OpenAPI openAPI = new OpenAPI();
        openAPI.setServers(Collections.singletonList(
                new io.swagger.v3.oas.models.servers.Server().url("http://fromopenapi.com")
        ));

        args.validateValidServer(spec, openAPI);
        Assertions.assertThat(args.getServer()).isEqualTo("http://fromopenapi.com");
    }

    @Test
    void shouldReplaceServerPlaceholder() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ApiArguments args = new ApiArguments();
        args.setServer("http://api.com");

        OpenAPI openAPI = new OpenAPI();
        openAPI.setServers(Collections.singletonList(
                new io.swagger.v3.oas.models.servers.Server().url("{apiRoot}/v2")
        ));
        args.validateValidServer(spec, openAPI);
        // The placeholder should be replaced
        Assertions.assertThat(args.getServer()).isEqualTo("http://api.com/v2");
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
}
