package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

@QuarkusTest
class InfoCommandTest {
    @Inject
    InfoCommand injectedInfoCommand;

    private InfoCommand infoCommand;

    @BeforeEach
    void setup() {
        infoCommand = Mockito.spy(injectedInfoCommand);
        ReflectionTestUtils.setField(infoCommand, "catsCommand", Mockito.mock(CatsCommand.class));
    }

    @Test
    void shouldReturnInfoInText() {
        CommandLine commandLine = new CommandLine(infoCommand);
        commandLine.execute();

        Mockito.verify(infoCommand, Mockito.times(1)).displayText(Mockito.any());
        Mockito.verify(infoCommand, Mockito.times(0)).displayJson(Mockito.any());
    }

    @Test
    void shouldReturnInfoInJson() {
        CommandLine commandLine = new CommandLine(infoCommand);
        commandLine.execute("-j");

        Mockito.verify(infoCommand, Mockito.times(0)).displayText(Mockito.any());
        Mockito.verify(infoCommand, Mockito.times(1)).displayJson(Mockito.any());
    }
}
