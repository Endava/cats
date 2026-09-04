package com.endava.cats.command;

import com.endava.cats.args.StopArguments;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

@QuarkusTest
class CatsCommandStopArgumentsTest {
    @Inject
    CatsCommand catsCommand;

    @Inject
    CommandLine.IFactory factory;

    @Inject
    StopArguments stopArguments;

    @AfterEach
    void resetStopArguments() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterMutations", 0);
    }

    @Test
    void shouldExposeStopAfterTestsOnTheMainCommand() {
        CommandLine commandLine = new CommandLine(catsCommand, factory);

        CommandLine.ParseResult result = commandLine.parseArgs("--stopAfterTests", "7", "--help");

        Assertions.assertThat(result.hasMatchedOption("--stopAfterTests")).isTrue();
        Assertions.assertThat(stopArguments.getStopAfterMutations()).isEqualTo(7);
    }

    @Test
    void shouldKeepStopAfterTestsAvailableOnTheRandomCommand() {
        CommandLine commandLine = new CommandLine(catsCommand, factory);

        CommandLine.ParseResult result = commandLine.parseArgs("random", "--stopAfterTests", "9", "--help");

        Assertions.assertThat(result.subcommand().hasMatchedOption("--stopAfterTests")).isTrue();
        Assertions.assertThat(stopArguments.getStopAfterMutations()).isEqualTo(9);
    }
}
