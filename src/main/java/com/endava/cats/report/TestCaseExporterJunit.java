package com.endava.cats.report;

import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.github.mustachejava.Mustache;
import jakarta.inject.Inject;
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
    private static final Mustache JUNIT_SUMMARY_MUSTACHE = mustacheFactory.compile("junit_summary.mustache");
    private static final String JUNIT_XML = "junit.xml";

    /**
     * Constructs a new instance of TestCaseExporterJunit with the specified reporting arguments.
     *
     * @param reportingArguments the reporting arguments
     * @param catsGlobalContext  the global context
     * @param qualityGateArguments the quality gate arguments
     * @param executionStatisticsListener the execution statistics listener
     */
    @Inject
    public TestCaseExporterJunit(ReportingArguments reportingArguments, CatsGlobalContext catsGlobalContext,
                                 QualityGateArguments qualityGateArguments,
                                 ExecutionStatisticsListener executionStatisticsListener) {
        super(reportingArguments, catsGlobalContext, qualityGateArguments, executionStatisticsListener);
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
