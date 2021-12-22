package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

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
        Assertions.assertThatThrownBy(() -> apiArguments.validateRequired(spec))
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
}
