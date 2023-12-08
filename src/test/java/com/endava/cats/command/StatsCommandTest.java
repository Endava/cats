package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

@QuarkusTest
class StatsCommandTest {

    private StatsCommand statsCommand;

    @BeforeEach
    void setup() {
        statsCommand = new StatsCommand();
    }

    @Test
    void shouldRunStats() {
        StatsCommand statsCommandSpy = Mockito.spy(statsCommand);
        CommandLine commandLine = new CommandLine(statsCommandSpy);
        commandLine.execute("--contract", "src/test/resources/openapi.yml");
        Mockito.verify(statsCommandSpy, Mockito.times(1)).displayText(Mockito.any());
        commandLine.execute("--contract", "src/test/resources/openapi.yml", "-j");
        Mockito.verify(statsCommandSpy, Mockito.times(1)).displayJson(Mockito.any());
    }

}
