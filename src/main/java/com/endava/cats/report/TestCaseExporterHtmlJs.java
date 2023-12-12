package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.github.mustachejava.Mustache;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("htmlJs")
public class TestCaseExporterHtmlJs extends TestCaseExporter {

    public TestCaseExporterHtmlJs(ReportingArguments reportingArguments) {
        super(reportingArguments);
    }

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"styles.css", "script.js"};
    }

    @Override
    public ReportingArguments.ReportFormat reportFormat() {
        return ReportingArguments.ReportFormat.HTML_JS;
    }

    @Override
    protected boolean isJavascript() {
        return true;
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
