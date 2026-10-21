package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.github.mustachejava.Mustache;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.webfuzzing.commons.report.Report;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Concrete {@link TestCaseExporter} that generates a standardized Web Fuzzing Commons (WFC) {@code report.json}
 * report with WFC Faults classification. Can be used standalone ({@code --reportFormat WFC_REPORT}) or combined
 * with other formats ({@code --reportFormat HTML_JS,WFC_REPORT}).
 */
@Singleton
@Named("wfcReport")
public class TestCaseExporterWfcReport extends TestCaseExporter {
    public static final String WFC_REPORT_FILE = "report.json";

    private static final Gson WFC_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return "additionalProperties".equals(f.getName());
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();

    @Inject
    public TestCaseExporterWfcReport(ReportingArguments reportingArguments,
                                     CatsGlobalContext catsGlobalContext,
                                     ProcessingArguments processingArguments,
                                     StopArguments stopArguments) {
        super(reportingArguments, catsGlobalContext, processingArguments, stopArguments);
    }

    @Override
    @DryRun
    public void writeTestCase(CatsTestCase testCase) {
        writeJsonTestCase(testCase);
    }

    @Override
    public void writeSummary(List<CatsTestCaseSummary> summaries, ExecutionSummary executionSummary) {
        long executionTimeSeconds = Math.max(0, (System.currentTimeMillis() - getT0()) / 1000);
        Report wfcReport = WfcReportBuilder.build(
                summaries,
                executionSummary,
                catsGlobalContext.getOpenAPI(),
                catsGlobalContext.getRecordedErrors(),
                getReportingPath(),
                getAppVersion(),
                executionTimeSeconds);

        try {
            Path target = Paths.get(getReportingPath().toFile().getAbsolutePath(), WFC_REPORT_FILE);
            String json = WFC_GSON.toJson(wfcReport);
            Files.writeString(target, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw reportFailure("Unable to write WFC " + WFC_REPORT_FILE, e);
        }
    }

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[0];
    }

    @Override
    public ReportingArguments.ReportFormat reportFormat() {
        return ReportingArguments.ReportFormat.WFC_REPORT;
    }

    @Override
    public Mustache getSummaryTemplate() {
        return SUMMARY_MUSTACHE;
    }

    @Override
    public String getSummaryReportTitle() {
        return WFC_REPORT_FILE;
    }
}
