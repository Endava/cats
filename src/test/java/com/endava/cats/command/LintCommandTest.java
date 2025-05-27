package com.endava.cats.command;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
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


    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(lintCommand, "contract", "contract");
        ReflectionTestUtils.setField(filterArguments, "fuzzersToBeRunComputed", false);
        ReflectionTestUtils.setField(lintCommand, "catsCommand", catsCommand);
        ReflectionTestUtils.setField(lintCommand, "skipLinters", Collections.emptyList());
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));

    }

    @Test
    void shouldRunContractFuzzers() {
        ReflectionTestUtils.setField(lintCommand, "contract", "src/test/resources/petstore-empty.yml");

        lintCommand.run();
        Assertions.assertThat(filterArguments.getFirstPhaseFuzzersForPath()).hasSize(37);
        Mockito.verify(testCaseListener, Mockito.times(37)).afterFuzz("/pets/{id}");
    }

    @Test
    void shouldReturnNonZeroExitCode() {
        ExecutionStatisticsListener listener = Mockito.mock(ExecutionStatisticsListener.class);
        Mockito.when(listener.getErrors()).thenReturn(10);
        ReflectionTestUtils.setField(catsCommand, "executionStatisticsListener", listener);
        int exitCode = lintCommand.getExitCode();
        Assertions.assertThat(exitCode).isEqualTo(10);
    }
}
