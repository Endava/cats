package com.endava.cats.command;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.report.CatsTestCase;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * This will replay a given list of tests solely based on the information received in the test case file(s).
 */
@Component
public class ReplayCommand {
    private static final PrettyLogger LOGGER = PrettyLogger.fromSlf4j(LoggerFactory.getLogger(ReplayCommand.class));
    private final ServiceCaller serviceCaller;
    private final FilterArguments filterArguments;

    @Autowired
    public ReplayCommand(ServiceCaller serviceCaller, FilterArguments filterArguments) {
        this.serviceCaller = serviceCaller;
        this.filterArguments = filterArguments;
    }

    public void execute() {
        if (filterArguments.areTestCasesSupplied()) {
            List<String> testCaseFileNames = filterArguments.parseTestCases();
            for (String testCaseFileName : testCaseFileNames) {
                try {
                    LOGGER.start("Executing {}", testCaseFileName);
                    this.executeTestCase(testCaseFileName);
                    LOGGER.complete("Finish executing {}", testCaseFileName);
                } catch (IOException e) {
                    LOGGER.error("Something went wrong while replaying {}: {}", testCaseFileName, e.toString());
                }
            }
        } else {
            LOGGER.warn("You must supply a list of test cases using the --testCases argument!");
        }
    }

    public void executeTestCase(String testCaseFileName) throws IOException {
        String testCaseFile = Files.readString(Paths.get(testCaseFileName));
        LOGGER.note("Loaded content: \n" + testCaseFile);
        CatsTestCase testCase = TestCaseExporter.GSON.fromJson(testCaseFile, CatsTestCase.class);
        CatsResponse response = serviceCaller.callService(testCase.getRequest(), Collections.emptySet());

        LOGGER.note("Response body: \n{}", TestCaseExporter.GSON.toJson(response.getJsonBody()));
    }
}
