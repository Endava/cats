package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.github.mustachejava.Mustache;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("junit")
public class TestCaseExporterJunit extends TestCaseExporter {

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
