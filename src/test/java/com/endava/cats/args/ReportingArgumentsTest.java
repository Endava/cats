package com.endava.cats.args;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.config.level.PrettyLevel;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.util.Map;

@QuarkusTest
class ReportingArgumentsTest {
    @Test
    void shouldEnableTerminalInterfaceFromCommandLine() {
        ReportingArguments arguments = new ReportingArguments();

        new CommandLine(arguments).parseArgs("--tui", "--tuiMaxResults", "250");

        Assertions.assertThat(arguments.isTui()).isTrue();
        Assertions.assertThat(arguments.getTuiMaxResults()).isEqualTo(250);
    }

    @Test
    void shouldRestoreConfiguredLevelsAfterTui() {
        ReportingArguments arguments = new ReportingArguments();
        new CommandLine(arguments).parseArgs("--tui", "--verbosity", "DETAILED", "--onlyLog", "error,success");

        try {
            arguments.processLogData();
            arguments.restoreLogDataAfterTui();

            @SuppressWarnings("unchecked")
            Map<String, Boolean> levels = (Map<String, Boolean>) ReflectionTestUtils.getField(PrettyLogger.class, "LEVELS_MAP");
            Assertions.assertThat(levels).containsEntry("ERROR", true).containsEntry("SUCCESS", true)
                    .containsEntry("INFO", false);
        } finally {
            PrettyLogger.enableLevels(PrettyLevel.values());
        }
    }
}
