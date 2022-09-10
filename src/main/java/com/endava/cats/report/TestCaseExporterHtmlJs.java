package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.report.CatsTestCaseSummary;
import com.endava.cats.model.report.CatsTestReport;
import com.github.mustachejava.Mustache;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Named("htmlJs")
public class TestCaseExporterHtmlJs extends TestCaseExporter {

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"cats.png", "styles.css", "script.js", "jquery.min.js"};
    }

    @Override
    public Map<String, Object> getSpecificContext(CatsTestReport report) {
        Map<String, Object> context = new HashMap<>();
        List<String> fuzzers = report.getTestCases().stream()
                .map(CatsTestCaseSummary::getFuzzer)
                .distinct().sorted()
                .toList();

        context.put("FUZZERS", fuzzers);
        context.put("JS", true);
        return context;
    }

    @Override
    public ReportingArguments.ReportFormat reportFormat() {
        return ReportingArguments.ReportFormat.HTML_JS;
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
