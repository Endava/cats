package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.github.mustachejava.Mustache;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A concrete implementation of TestCaseExporter for exporting test case results in JUnit format.
 * This class extends the base TestCaseExporter and provides specific functionality for JUnit reporting.
 *
 * @see TestCaseExporter
 */
@Singleton
@Named("junit")
public class TestCaseExporterJunit extends TestCaseExporter {

    /**
     * Constructs a new instance of TestCaseExporterJunit with the specified reporting arguments.
     *
     * @param reportingArguments the reporting arguments for configuring the TestCaseExporterJunit
     */
    public TestCaseExporterJunit(ReportingArguments reportingArguments) {
        super(reportingArguments);
    }

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[0];
    }

    @Override
    public ReportingArguments.ReportFormat reportFormat() {
        return ReportingArguments.ReportFormat.JUNIT;
    }

    @Override
    public Mustache getSummaryTemplate() {
        return JUNIT_SUMMARY_MUSTACHE;
    }

    @Override
    public String getSummaryReportTitle() {
        return JUNIT_XML;
    }
}
