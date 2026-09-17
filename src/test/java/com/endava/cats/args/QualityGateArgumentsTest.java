package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

@QuarkusTest
class QualityGateArgumentsTest {

    private QualityGateArguments qualityGateArguments;

    @BeforeEach
    void setUp() {
        qualityGateArguments = new QualityGateArguments();
    }


    @Test
    void shouldFailOnErrorsByDefault() {
        // Default behavior: fail on any error
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isFalse();
    }

    @Test
    void shouldFailOnErrorsWhenExplicitlyConfigured() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 10)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
    }

    @Test
    void shouldFailOnWarningsWhenConfigured() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "warn");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isFalse();
    }

    @Test
    void shouldFailOnErrorsOrWarningsWhenBothConfigured() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error,warn");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 1)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isFalse();
    }

    @Test
    void shouldHandleCaseInsensitiveFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "ERROR,WARN");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
    }

    @Test
    void shouldHandleWhitespaceInFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", " error , warn ");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "errors<5, 4, 0, false",
            "errors<5, 5, 0, true",
            "errors<5, 6, 0, true",
            "warns<10, 0, 9, false",
            "warns<10, 0, 10, true",
            "warns<10, 0, 11, true",
            "errors>5, 6, 0, false",
            "errors>5, 5, 0, true",
            "errors>5, 4, 0, true",
            "warns>10, 0, 11, false",
            "warns>10, 0, 10, true",
            "warns>10, 0, 9, true"
    })
    void shouldEvaluateLessThanAndGreaterThanQualityGates(String gate, int errors, int warnings, boolean shouldFail) {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", gate);

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(errors, warnings)).isEqualTo(shouldFail);
    }

    @Test
    void shouldEvaluateMultipleQualityGates() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<5,warns<20");

        // Both pass
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 19)).isFalse();

        // Errors fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 19)).isTrue();

        // Warnings fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 20)).isTrue();

        // Both fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 20)).isTrue();
    }

    @Test
    void shouldSupportWarningsAlias() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "warnings<10");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 9)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isTrue();
    }

    @Test
    void shouldHandleWhitespaceInQualityGate() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", " errors < 5 , warns < 20 ");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 19)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 19)).isTrue();
    }

    @Test
    void shouldHandleEmptyQualityGate() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "");

        // Should fall back to default behavior
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"errors=5", "invalid<5", "errors<abc", "errors<", "<5", "errors<<5", "errors<-1", "errors<9223372036854775808"})
    void shouldRejectInvalidQualityGateAsUsageError(String gate) {
        Assertions.assertThat(execute("--qualityGate", gate)).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void shouldAcceptValidQualityGateOptionsDuringParsing() {
        Assertions.assertThat(execute("--qualityGate", "ERRORS<5,warnings>1"))
                .isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(execute("--failOn", "ERROR,WARN"))
                .isEqualTo(CommandLine.ExitCode.OK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"failure", "error,failure", "error,,warn"})
    void shouldRejectInvalidFailOnAsUsageError(String failOn) {
        Assertions.assertThat(execute("--failOn", failOn)).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void shouldPrioritizeQualityGateOverFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<10");
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error");

        // Quality gate takes precedence
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 0)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(10, 0)).isTrue();
    }

    @Test
    void shouldGetQualityGateDescription() {
        Assertions.assertThat(qualityGateArguments.getQualityGateDescription())
                .isEqualTo("Default: fail on any error");

        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error,warn");
        Assertions.assertThat(qualityGateArguments.getQualityGateDescription())
                .isEqualTo("Fail on: error,warn");

        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<5,warns<20");
        Assertions.assertThat(qualityGateArguments.getQualityGateDescription())
                .isEqualTo("Quality gate: errors<5,warns<20");
    }

    @Test
    void shouldHandleEmptyFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "");

        // Should fall back to default behavior
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
    }

    @Test
    void shouldRejectEmptyConditionsInQualityGate() {
        Assertions.assertThat(execute("--qualityGate", "errors<5,,warns<20"))
                .isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void shouldHandleZeroThresholds() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<0");

        // Any error should fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
    }

    @Test
    void shouldHandleLargeNumbers() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<1000000");

        Assertions.assertThat(qualityGateArguments.shouldFailBuild(999999, 0)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1000000, 0)).isTrue();
    }

    private int execute(String... args) {
        CommandLine commandLine = new CommandLine(new QualityGateCommand());
        commandLine.setErr(new PrintWriter(new StringWriter()));
        return commandLine.execute(args);
    }

    @CommandLine.Command
    static class QualityGateCommand implements Runnable {
        @CommandLine.ArgGroup(exclusive = false)
        QualityGateArguments arguments = new QualityGateArguments();

        @Override
        public void run() {
        }
    }
}
