package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.github.mustachejava.Mustache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("junit")
public class TestCaseExporterJunit extends TestCaseExporter {

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
