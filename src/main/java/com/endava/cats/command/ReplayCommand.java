package com.endava.cats.command;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.report.CatsTestCase;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This will replay a given list of tests solely based on the information received in the test case file(s).
 */
@CommandLine.Command(
        name = "replay",
        mixinStandardHelpOptions = true,
        usageHelpWidth = 100,
        description = "Replay previously executed CATS tests",
        helpCommand = true,
        version = "cats replay 7.0.0")
@Component
public class ReplayCommand implements Runnable {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(ReplayCommand.class);
    private final ServiceCaller serviceCaller;

    @CommandLine.Option(
            names = {"-t", "--tests"},
            description = "The list of CATS tests. If you provide the .json extension it will be considered a path, " +
                    "otherwise it will look for that test in the cats-report folder",
            required = true, split = ",")
    List<String> tests;

    @Autowired
    public ReplayCommand(ServiceCaller serviceCaller) {
        this.serviceCaller = serviceCaller;
    }

    public List<String> parseTestCases() {
        return tests.stream()
                .map(testCase -> testCase.trim().strip())
                .map(testCase -> testCase.endsWith(".json") ? testCase : "cats-report/" + testCase + ".json")
                .collect(Collectors.toList());
    }

    public void executeTestCase(String testCaseFileName) throws IOException {
        String testCaseFile = Files.readString(Paths.get(testCaseFileName));
        LOGGER.note("Loaded content: \n" + testCaseFile);
        CatsTestCase testCase = TestCaseExporter.GSON.fromJson(testCaseFile, CatsTestCase.class);
        CatsResponse response = serviceCaller.callService(testCase.getRequest(), Collections.emptySet());

        LOGGER.note("Response body: \n{}", TestCaseExporter.GSON.toJson(response.getJsonBody()));
    }

    @Override
    public void run() {
        for (String testCaseFileName : this.parseTestCases()) {
            try {
                LOGGER.start("Executing {}", testCaseFileName);
                this.executeTestCase(testCaseFileName);
                LOGGER.complete("Finish executing {}", testCaseFileName);
            } catch (IOException e) {
                LOGGER.error("Something went wrong while replaying {}: {}", testCaseFileName, e.toString());
            }
        }
    }
}
