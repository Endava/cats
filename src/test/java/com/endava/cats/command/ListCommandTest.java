package com.endava.cats.command;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

@QuarkusTest
class ListCommandTest {

    @Inject
    Instance<Fuzzer> fuzzers;

    private ListCommand listCommand;

    @BeforeEach
    void setup() {
        listCommand = new ListCommand(fuzzers);
    }

    @Test
    void shouldListFuzzers() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-f");
        Mockito.verify(spyListCommand, Mockito.times(1)).listFuzzers();
    }

    @Test
    void shouldNotListFuzzers() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-f=false");
        Mockito.verify(spyListCommand, Mockito.times(0)).listFuzzers();
    }

    @Test
    void shouldListFuzzersStrategies() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-s");
        Mockito.verify(spyListCommand, Mockito.times(1)).listFuzzerStrategies();
    }

    @Test
    void shouldNotListFuzzersStrategies() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-s=false");
        Mockito.verify(spyListCommand, Mockito.times(0)).listFuzzerStrategies();
    }

    @Test
    void shouldListContractPaths() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-p", "-c", "src/test/resources/openapi.yml");
        Mockito.verify(spyListCommand, Mockito.times(1)).listContractPaths();
    }

    @Test
    void shouldNotListContractPaths() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-p=false", "-c", "src/test/resources/openapi.yml");
        Mockito.verify(spyListCommand, Mockito.times(0)).listContractPaths();
    }

    @Test
    void shouldThrowIOException() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute("-p", "-c", "openapi.yml");
        Mockito.verify(spyListCommand, Mockito.times(1)).listContractPaths();
    }

    @Test
    void shouldNotList() {
        ListCommand spyListCommand = Mockito.spy(listCommand);
        CommandLine commandLine = new CommandLine(spyListCommand);
        commandLine.execute();
        Mockito.verifyNoInteractions(spyListCommand);
    }

    static class DummyFuzzer implements Fuzzer {

        @Override
        public void fuzz(FuzzingData data) {

        }

        @Override
        public String description() {
            return null;
        }
    }
}
