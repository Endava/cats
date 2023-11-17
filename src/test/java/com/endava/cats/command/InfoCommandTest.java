package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

@QuarkusTest
class InfoCommandTest {
    @Inject
    InfoCommand infoCommand;

    @Test
    void shouldReturnInfoInText() {
        infoCommand = Mockito.spy(infoCommand);
        CommandLine commandLine = new CommandLine(infoCommand);
        commandLine.execute();

        Mockito.verify(infoCommand, Mockito.times(1)).displayText(Mockito.any());
        Mockito.verify(infoCommand, Mockito.times(0)).displayJson(Mockito.any());
    }

    @Test
    void shouldReturnInfoInJson() {
        infoCommand = Mockito.spy(infoCommand);
        CommandLine commandLine = new CommandLine(infoCommand);
        commandLine.execute("-j");

        Mockito.verify(infoCommand, Mockito.times(0)).displayText(Mockito.any());
        Mockito.verify(infoCommand, Mockito.times(1)).displayJson(Mockito.any());
    }
}
