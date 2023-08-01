package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.github.mustachejava.Mustache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("htmlOnly")
public class TestCaseExporterHtmlOnly extends TestCaseExporter {

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"styles.css"};
    }

    @Override
    public ReportingArguments.ReportFormat reportFormat() {
        return ReportingArguments.ReportFormat.HTML_ONLY;
    }

    @Override
    public Mustache getSummaryTemplate() {
        return SUMMARY_MUSTACHE;
    }

    @Override
    public String getSummaryReportTitle() {
        return REPORT_HTML;
    }

}
