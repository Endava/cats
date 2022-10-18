package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsTestReport;
import com.github.mustachejava.Mustache;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.util.Collections;
import java.util.Map;

@ApplicationScoped
@Named("htmlOnly")
public class TestCaseExporterHtmlOnly extends TestCaseExporter {

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"cats.png", "styles.css"};
    }

    @Override
    public Map<String, Object> getSpecificContext(CatsTestReport report) {
        return Collections.emptyMap();
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
