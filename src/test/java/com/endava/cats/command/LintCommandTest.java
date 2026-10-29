package com.endava.cats.command;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

@QuarkusTest
class LintCommandTest {
    @Inject
    ReportingArguments reportingArguments;
    @Inject
    LintCommand lintCommand;
    @Inject
    CatsCommand catsCommand;
    @InjectSpy
    TestCaseListener testCaseListener;
    @Inject
    FilterArguments filterArguments;
    picocli.CommandLine.Model.CommandSpec spec;


    @BeforeEach
    void init() {
        picocli.CommandLine commandLine = new picocli.CommandLine(lintCommand);
        spec = commandLine.getCommandSpec();
        ReflectionTestUtils.setField(lintCommand, "contract", "contract");
        ReflectionTestUtils.setField(filterArguments, "fuzzersToBeRunComputed", false);
        ReflectionTestUtils.setField(filterArguments, "profile", "full");
        ReflectionTestUtils.setField(lintCommand, "catsCommand", catsCommand);
        ReflectionTestUtils.setField(catsCommand, "spec", spec);
        ReflectionTestUtils.setField(lintCommand, "skipLinters", Collections.emptyList());
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldRunContractFuzzers() {
        ReflectionTestUtils.setField(lintCommand, "contract", "src/test/resources/petstore-empty.yml");

        lintCommand.run();
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).hasSize(58);
        Mockito.verify(testCaseListener, Mockito.times(53)).afterFuzz("/pets/{id}");
    }

    @Test
    void shouldRunOnlySelectedLintersWhenLintersOptionProvided() {
        ReflectionTestUtils.setField(lintCommand, "contract", "src/test/resources/petstore-empty.yml");
        ReflectionTestUtils.setField(lintCommand, "linters", List.of("PathPlurals", "TopLevelElements"));

        try {
            lintCommand.run();
            Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath())
                    .containsExactlyInAnyOrder("PathPluralsLinter", "TopLevelElementsLinter");
        } finally {
            ReflectionTestUtils.setField(lintCommand, "linters", null);
        }
    }

    @Test
    void shouldReturnNonZeroExitCode() {
        ExecutionStatisticsListener listener = Mockito.mock(ExecutionStatisticsListener.class);
        Mockito.when(listener.getErrors()).thenReturn(10L);
        Mockito.when(listener.getWarns()).thenReturn(0L);
        ReflectionTestUtils.setField(catsCommand, "executionStatisticsListener", listener);
        int exitCode = lintCommand.getExitCode();
        Assertions.assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void shouldParseSkipLintersForExtensionWithoutSplittingCommaSeparatedLinters() {
        new picocli.CommandLine(lintCommand).parseArgs(
                "-c", "src/test/resources/petstore-empty.yml",
                "--skipLintersForExtension", "x-public=true:PathPlurals,TopLevelElements,x-internal=yes:Versions");

        try {
            lintCommand.run();
            var result = filterArguments.getSkipFuzzersForExtension();
            Assertions.assertThat(result).containsKeys("x-public", "x-internal");
            Assertions.assertThat(result.get("x-public").get("true"))
                    .containsExactly("PathPlurals", "TopLevelElements");
            Assertions.assertThat(result.get("x-internal").get("yes"))
                    .containsExactly("Versions");
        } finally {
            ReflectionTestUtils.setField(lintCommand, "skipLintersForExtension", null);
            ReflectionTestUtils.setField(filterArguments, "skipFuzzersForExtension", null);
            filterArguments.getSkipFuzzersForExtension().clear();
        }
    }
}
