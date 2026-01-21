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
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).hasSize(41);
        Mockito.verify(testCaseListener, Mockito.times(41)).afterFuzz("/pets/{id}");
    }

    @Test
    void shouldReturnNonZeroExitCode() {
        ExecutionStatisticsListener listener = Mockito.mock(ExecutionStatisticsListener.class);
        Mockito.when(listener.getErrors()).thenReturn(10);
        Mockito.when(listener.getWarns()).thenReturn(0);
        ReflectionTestUtils.setField(catsCommand, "executionStatisticsListener", listener);
        int exitCode = lintCommand.getExitCode();
        Assertions.assertThat(exitCode).isEqualTo(1);
    }
}
