package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.github.mustachejava.Mustache;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A concrete implementation of TestCaseExporter for exporting test case results in HTML format with JavaScript.
 * This class extends the base TestCaseExporter and provides specific functionality for HTML with JavaScript reporting.
 *
 * @see TestCaseExporter
 */
@Singleton
@Named("htmlJs")
public class TestCaseExporterHtmlJs extends TestCaseExporter {

    /**
     * Constructs a new instance of TestCaseExporterHtmlJs with the specified reporting arguments.
     *
     * @param reportingArguments the reporting arguments for configuring the TestCaseExporterHtmlJs
     * @param catsGlobalContext  the global context for the CATS application
     */
    public TestCaseExporterHtmlJs(ReportingArguments reportingArguments, CatsGlobalContext catsGlobalContext) {
        super(reportingArguments, catsGlobalContext);
    }

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"styles.css", "script.js", "chart.js", "draw_chart.js", "draw_response_codes_chart.js", "draw_top_failing_paths_chart.js"};
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
