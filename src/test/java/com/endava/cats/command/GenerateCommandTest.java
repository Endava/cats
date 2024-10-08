package com.endava.cats.command;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.factory.FuzzingDataFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import picocli.CommandLine;

import java.util.List;
import java.util.Set;

@QuarkusTest
class GenerateCommandTest {

    private GenerateCommand generateCommand;

    @Inject
    FuzzingDataFactory fuzzingDataFactory;

    @Inject
    CatsGlobalContext catsGlobalContext;

    @BeforeEach
    public void init() {
        generateCommand = new GenerateCommand(fuzzingDataFactory, catsGlobalContext);
    }

    @ParameterizedTest
    @CsvSource({"--path", "--contract"})
    void shouldCheckRequiredArguments(String option) {
        CommandLine commandLine = new CommandLine(generateCommand);
        Assertions.assertThat(commandLine.getCommandSpec().findOption(option).required()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"--pretty", "--httpMethod", "--limit", "--debug"})
    void shouldCheckNotRequiredArguments(String option) {
        CommandLine commandLine = new CommandLine(generateCommand);
        Assertions.assertThat(commandLine.getCommandSpec().findOption(option).required()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldRunWhenContractAndPath(String pretty) {
        GenerateCommand generateCommandSpy = Mockito.spy(generateCommand);
        CommandLine commandLine = new CommandLine(generateCommandSpy);
        commandLine.execute("--contract", "src/test/resources/openapi.yml", "--path", "/pet", "--pretty=" + pretty);
        String expected = "{\"photoUrls\":[\"P5PwcLZvlk\",\"oj6R4ofp4cwK4\"],\"name\":\"doggie\",\"id\":5,\"category\":{\"name\":\"X3lQg\",\"id\":9},\"tags\":[{\"name\":\"AuhKH6\",\"id\":7},{\"name\":\"2fS2xnF\",\"id\":2}],\"status\":\"available\"}";

        ArgumentCaptor<List<String>> argumentCaptor = ArgumentCaptor.forClass(List.class);

        Mockito.verify(generateCommandSpy).printResult(argumentCaptor.capture());
        List<String> actual = argumentCaptor.getValue();

        Assertions.assertThat(actual).hasSize(1);

        JsonObject jsonObject1 = new Gson().fromJson(expected, JsonObject.class);
        JsonObject jsonObject2 = new Gson().fromJson(actual.getFirst(), JsonObject.class);

        // Compare keys
        Set<String> keys1 = jsonObject1.keySet();
        Set<String> keys2 = jsonObject2.keySet();
        Assertions.assertThat(keys1).isEqualTo(keys2);
    }

    @ParameterizedTest
    @CsvSource({"not_existent", "src/test/resources/empty.yml", "src/test/resources/no-paths.yml", "src/test/resources/empty-paths.yml"})
    void shouldThrowExceptionWhenContractIssues(String contract) {
        CommandLine commandLine = new CommandLine(generateCommand);
        commandLine.execute("--contract", contract, "--path", "/pet", "--debug");
        Assertions.assertThat(generateCommand.getExitCode()).isEqualTo(192);
    }
}
