package com.endava.cats.command;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This will replay a given list of tests solely based on the information received in the test case file(s).
 */
@CommandLine.Command(
        name = "replay",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Replay previously executed CATS tests",
        versionProvider = VersionProvider.class)
@Dependent
public class ReplayCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ReplayCommand.class);
    private final ServiceCaller serviceCaller;

    @CommandLine.Parameters(
            description = "The list of CATS tests. If you provide the .json extension it will be considered a path, " +
                    "otherwise it will look for that test in the cats-report folder", split = ",", arity = "1..")
    String[] tests;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArgs;

    @CommandLine.Option(names = {"-D", "--debug"},
            description = "Set CATS log level to ALL. Useful for diagnose when raising bugs")
    private boolean debug;


    @Inject
    public ReplayCommand(ServiceCaller serviceCaller) {
        this.serviceCaller = serviceCaller;
    }

    public List<String> parseTestCases() {
        return Optional.of(Arrays.asList(tests)).orElse(Collections.emptyList()).stream()
                .map(testCase -> testCase.trim().strip())
                .map(testCase -> testCase.endsWith(".json") ? testCase : "cats-report/" + testCase + ".json")
                .collect(Collectors.toList());
    }

    public void executeTestCase(String testCaseFileName) throws IOException {
        String testCaseFile = Files.readString(Paths.get(testCaseFileName));
        logger.note("Loaded content: \n" + testCaseFile);
        CatsTestCase testCase = JsonUtils.GSON.fromJson(testCaseFile, CatsTestCase.class);
        logger.info("Calling service endpoints: {}", testCase.getRequest().getUrl());
        CatsResponse response = serviceCaller.callService(testCase.getRequest(), Collections.emptySet());

        logger.complete("Response body: \n{}", JsonUtils.GSON.toJson(response.getJsonBody()));
    }

    @Override
    public void run() {
        if (debug) {
            CatsUtil.setCatsLogLevel("ALL");
            logger.fav("Setting CATS log level to ALL!");
        }
        for (String testCaseFileName : this.parseTestCases()) {
            try {
                logger.start("Executing {}", testCaseFileName);
                this.executeTestCase(testCaseFileName);
                logger.complete("Finish executing {}", testCaseFileName);
            } catch (IOException e) {
                logger.debug("Exception while replaying test!", e);
                logger.error("Something went wrong while replaying {}: {}", testCaseFileName, e.toString());
            }
        }
    }
}
