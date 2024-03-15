package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.io.File;

@QuarkusTest
class RunCommandTest {
    @Inject
    ApiArguments apiArguments;
    @Inject
    RunCommand runCommand;
    @Inject
    CatsCommand catsCommand;
    FilterArguments filterArguments;

    @BeforeEach
    void init() {
        filterArguments = Mockito.mock(FilterArguments.class);
        ReflectionTestUtils.setField(apiArguments, "contract", "contract");
        ReflectionTestUtils.setField(apiArguments, "server", "http://server");
        ReflectionTestUtils.setField(catsCommand, "filterArguments", filterArguments);
        ReflectionTestUtils.setField(runCommand, "catsCommand", catsCommand);
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(runCommand, "spec", spec);
    }

    @Test
    void shouldThrowExceptionWhenServerNotValid() {
        ReflectionTestUtils.setField(apiArguments, "server", "server");
        Assertions.assertThatThrownBy(() -> runCommand.run()).isInstanceOf(CommandLine.ParameterException.class);
    }


    @Test
    void shouldRunCustomFuzzer() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/functionalFuzzer.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter("FunctionalFuzzer");
    }

    @ParameterizedTest
    @CsvSource({"securityFuzzer.yml,1", "securityFuzzer-fieldTypes.yml,1"})
    void shouldRunSecurityFuzzer(String securityFuzzerFile, int times) {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/" + securityFuzzerFile));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(times)).customFilter("SecurityFuzzer");
    }

    @Test
    void shouldThrowExceptionWhenEmptyFile() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/nonExistent.yml"));
        Assertions.assertThatThrownBy(() -> runCommand.run()).isInstanceOf(CommandLine.ParameterException.class).hasMessage("You must provide a valid non-empty <file>");
    }

    @Test
    void shouldReturnNonZeroExitCode() {
        ExecutionStatisticsListener listener = Mockito.mock(ExecutionStatisticsListener.class);
        Mockito.when(listener.getErrors()).thenReturn(10);
        ReflectionTestUtils.setField(catsCommand, "executionStatisticsListener", listener);
        int exitCode = runCommand.getExitCode();
        Assertions.assertThat(exitCode).isEqualTo(10);
    }
}
