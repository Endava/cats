package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.report.CatsTestReport;
import com.github.mustachejava.Mustache;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.util.Collections;
import java.util.Map;

@ApplicationScoped
@Named("junit")
public class TestCaseExporterJunit extends TestCaseExporter {

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[0];
    }

    @Override
    public Map<String, Object> getSpecificContext(CatsTestReport report) {
        return Collections.emptyMap();
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
