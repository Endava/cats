package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.github.mustachejava.Mustache;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A concrete implementation of TestCaseExporter for exporting test case results in HTML format only.
 * This class extends the base TestCaseExporter and provides specific functionality for HTML-only reporting.
 *
 * @see TestCaseExporter
 */
@Singleton
@Named("htmlOnly")
public class TestCaseExporterHtmlOnly extends TestCaseExporter {

    /**
     * Constructs a new instance of TestCaseExporterHtmlOnly with the specified reporting arguments.
     *
     * @param reportingArguments the reporting arguments for configuring the TestCaseExporterHtmlOnly
     * @param catsGlobalContext  the global context for the CATS application
     */
    public TestCaseExporterHtmlOnly(ReportingArguments reportingArguments, CatsGlobalContext catsGlobalContext) {
        super(reportingArguments, catsGlobalContext);
    }

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
