package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

@QuarkusTest
class RandomCommandTest {

    @Inject
    RandomCommand randomCommand;

    @BeforeEach
    void setup() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(randomCommand, "spec", spec);
    }

    @Test
    void shouldRunWithAllArguments() {
        ApiArguments apiArguments = new ApiArguments();
        apiArguments.setContract("contract");
        apiArguments.setServer("server");
        randomCommand.apiArguments = apiArguments;

        MatchArguments matchArguments = Mockito.mock(MatchArguments.class);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);

        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        Mockito.when(stopArguments.isAnyStopConditionProvided()).thenReturn(true);

        randomCommand.stopArguments = stopArguments;
        randomCommand.matchArguments = matchArguments;
        randomCommand.path = "/path";
        randomCommand.httpMethod = HttpMethod.POST;

        CatsCommand catsCommand = Mockito.mock(CatsCommand.class);
        randomCommand.catsCommand = catsCommand;
        catsCommand.filterArguments = Mockito.mock(FilterArguments.class);
        catsCommand.processingArguments = Mockito.mock(ProcessingArguments.class);
        randomCommand.run();
        Mockito.verify(catsCommand, Mockito.times(1)).run();
    }

    @Test
    void shouldReturnNonZeroExitCode() {
        CatsCommand catsCommand = Mockito.mock(CatsCommand.class);
        Mockito.when(catsCommand.getExitCode()).thenReturn(10);
        randomCommand.catsCommand = catsCommand;
        int exitCode = randomCommand.getExitCode();
        Assertions.assertThat(exitCode).isEqualTo(10);
    }
}
