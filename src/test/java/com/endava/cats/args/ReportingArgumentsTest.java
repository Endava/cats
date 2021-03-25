package com.endava.cats.args;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportingArgumentsTest {


    @Test
    void shouldPrintExecutionStatistics() {
        ReportingArguments reportingArguments = new ReportingArguments();

        ReflectionTestUtils.setField(reportingArguments, "printExecutionStatistics", "true");

        Assertions.assertThat(reportingArguments.printExecutionStatistics()).isTrue();
    }

    @Test
    void shouldNotPrintExecutionStatistics() {
        ReportingArguments reportingArguments = new ReportingArguments();

        ReflectionTestUtils.setField(reportingArguments, "printExecutionStatistics", "empty");

        Assertions.assertThat(reportingArguments.printExecutionStatistics()).isFalse();
    }

    @Test
    void shouldTimestampReports() {
        ReportingArguments reportingArguments = new ReportingArguments();

        ReflectionTestUtils.setField(reportingArguments, "timestampReports", "true");

        Assertions.assertThat(reportingArguments.isTimestampReports()).isTrue();
    }

    @Test
    void shouldNotTimestampReports() {
        ReportingArguments reportingArguments = new ReportingArguments();

        ReflectionTestUtils.setField(reportingArguments, "timestampReports", "empty");

        Assertions.assertThat(reportingArguments.isTimestampReports()).isFalse();
    }
}
