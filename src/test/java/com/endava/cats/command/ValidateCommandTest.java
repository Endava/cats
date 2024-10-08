package com.endava.cats.command;

import com.endava.cats.command.model.ValidContractEntry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import picocli.CommandLine;

@QuarkusTest
class ValidateCommandTest {

    private ValidateCommand validateCommand;

    @BeforeEach
    void setup() {
        validateCommand = new ValidateCommand();
    }

    @Test
    void shouldRunValidateWithValidContract() {
        ValidateCommand validateCommandSpy = Mockito.spy(validateCommand);
        CommandLine commandLine = new CommandLine(validateCommandSpy);
        commandLine.execute("--contract", "src/test/resources/openapi.yml");
        Mockito.verify(validateCommandSpy, Mockito.times(1)).displayText(ArgumentMatchers.argThat(validContractEntry ->
                validContractEntry.valid() && validContractEntry.reasons().getFirst().equalsIgnoreCase("valid")));
        commandLine.execute("--contract", "src/test/resources/openapi.yml", "-j");
        Mockito.verify(validateCommandSpy, Mockito.times(1)).displayJson(Mockito.any());
    }

    @Test
    void shouldBeInvalidWhenException() {
        ValidateCommand validateCommandSpy = Mockito.spy(validateCommand);
        CommandLine commandLine = new CommandLine(validateCommandSpy);
        commandLine.execute("--contract", "src/test/resources/notExistent.yml");
        Mockito.verify(validateCommandSpy, Mockito.times(1)).displayText(ArgumentMatchers.argThat(validContractEntry ->
                !validContractEntry.valid() && validContractEntry.reasons().getFirst().contains("NoSuchFileException")));
    }

    @Test
    void shouldValidateSwagger2() {
        ValidateCommand validateCommandSpy = Mockito.spy(validateCommand);
        CommandLine commandLine = new CommandLine(validateCommandSpy);
        commandLine.execute("--contract", "src/test/resources/issue77.json", "--detailed");
        Mockito.verify(validateCommandSpy, Mockito.times(1)).displayText(ArgumentMatchers.argThat(validContractEntry ->
                !validContractEntry.valid() && validContractEntry.reasons().getFirst().contains("attribute paths.'/authentication/token'(post).[grant_type].schema is unexpected")
                        && validContractEntry.reasons().size() == 11));
        Mockito.verify(validateCommandSpy, Mockito.times(1)).displayReasonLine(Mockito.any(ValidContractEntry.class));
    }

    @ParameterizedTest
    @CsvSource({"true,1", "false,0"})
    void shouldBeInvalidWhenContractInvalid(boolean detailed, int times) {
        ValidateCommand validateCommandSpy = Mockito.spy(validateCommand);
        CommandLine commandLine = new CommandLine(validateCommandSpy);
        commandLine.execute("--contract", "src/test/resources/petstore.yml", "--detailed=" + detailed);
        Mockito.verify(validateCommandSpy, Mockito.times(1)).displayText(ArgumentMatchers.argThat(validContractEntry ->
                !validContractEntry.valid() && validContractEntry.reasons().getFirst().contains("attribute paths.'/pets-batch'(post).operationId is repeated")));
        Mockito.verify(validateCommandSpy, Mockito.times(times)).displayReasonLine(Mockito.any(ValidContractEntry.class));
    }
}
