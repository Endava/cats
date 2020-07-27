package com.endava.cats.io;

import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.model.report.CatsTestCaseSummary;
import com.endava.cats.model.report.CatsTestReport;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class is responsible for writing the final report file(s)
 */
@Service
public class TestCaseExporter {
    private static final String TEST_CASES_FOLDER = "test-report";

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCaseExporter.class);
    private static final Path path;
    private static final String SOURCE = "SOURCE";
    private static final String SCRIPT = "<script type=\"text/javascript\" src=\"" + SOURCE + "\"></script>";
    private static final StringBuilder builder = new StringBuilder();
    private static final String VAR = "var";
    private static final String PLACEHOLDER = "PLACEHOLDER";
    private static final String REPORT_ZIP = "report.zip";
    private static final String SUMMARY = "summary";
    private static final String REPORT_HTML = "index.html";
    private static final String JAVASCRIPT_EXTENSION = ".js";

    static {
        path = Paths.get(TEST_CASES_FOLDER);
        if (!path.toFile().exists()) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                LOGGER.error("Exception while creating root test cases folder: {}", e.getMessage());
            }
        } else {
            deleteAllFilesFirst();
        }
    }

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private static void deleteAllFilesFirst() {
        for (File file : Objects.requireNonNull(path.toFile().listFiles())) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                LOGGER.error("Failed to delete test-report folder contents!");
            }
        }
    }

    public void writeSummary(Map<String, CatsTestCase> testCaseMap, int all, int success, int warnings, int errors) {
        Path testPath = Paths.get(path.toFile().getAbsolutePath(), SUMMARY.concat(JAVASCRIPT_EXTENSION));

        List<CatsTestCaseSummary> summaries = testCaseMap.entrySet().stream()
                .filter(entry -> entry.getValue().isNotSkipped())
                .map(testCase -> CatsTestCaseSummary.fromCatsTestCase(testCase.getKey(), testCase.getValue())).sorted()
                .collect(Collectors.toList());

        CatsTestReport report = CatsTestReport.builder().summaryList(summaries).errors(errors).success(success).totalTests(all).warnings(warnings).build();

        String toWrite = new GsonBuilder().setPrettyPrinting().setExclusionStrategies(new ExcludeTestCaseStrategy()).serializeNulls().create().toJson(report);
        toWrite = VAR + " " + SUMMARY + " = " + toWrite;
        this.write(SUMMARY, testPath, toWrite);
    }

    public void writeReportFiles() {
        try (ZipInputStream zipInputStream = new ZipInputStream(resolver.getResourceLoader().getResource(REPORT_ZIP).getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Files.copy(zipInputStream, Paths.get(path.toFile().getAbsolutePath(), entry.getName()));
            }
            try (Stream<String> index = Files.lines(Paths.get(path.toFile().getAbsolutePath(), REPORT_HTML))) {
                List<String> updatedIndex = index.map(line -> line.replace(PLACEHOLDER, builder.toString())).collect(Collectors.toList());
                Files.write(Paths.get(path.toFile().getAbsolutePath(), REPORT_HTML), updatedIndex);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to write reporting files!", e);
        }
    }

    public void writeToFile(CatsTestCase testCase) {
        String testCaseName = MDC.get("id").replace(" ", "");
        Path testPath = Paths.get(path.toFile().getAbsolutePath(), testCaseName.concat(JAVASCRIPT_EXTENSION));

        String toWrite = new GsonBuilder().setLenient().setPrettyPrinting().setExclusionStrategies(new ExcludeTestCaseStrategy()).serializeNulls().create().toJson(testCase);
        toWrite = VAR + " " + testCaseName + " = " + toWrite;
        builder.append(SCRIPT.replace(SOURCE, testCaseName.concat(JAVASCRIPT_EXTENSION))).append(System.lineSeparator());
        write(testCaseName, testPath, toWrite);
    }

    private void write(String id, Path testPath, String toWrite) {
        try (BufferedWriter writer = Files.newBufferedWriter(testPath)) {

            writer.write(toWrite);
            LOGGER.info("Finish writing test case {} to file {}", id, testPath);
        } catch (IOException e) {
            LOGGER.warn("Something went wrong while writing test case {}: {}", id, e.getMessage(), e);
        }
    }
}
