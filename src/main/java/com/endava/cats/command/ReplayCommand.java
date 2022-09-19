package com.endava.cats.command;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.KeyValuePair;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.VersionProvider;
import com.google.common.collect.Maps;
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
import java.util.Map;
import java.util.Optional;

/**
 * This will replay a given list of tests solely based on the information received in the test case file(s).
 */
@CommandLine.Command(
        name = "replay",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "Replay previously executed CATS tests",
        versionProvider = VersionProvider.class)
@Dependent
public class ReplayCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ReplayCommand.class);
    private final ServiceCaller serviceCaller;
    private final CatsDSLParser catsDSLParser;

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

    @CommandLine.Option(names = {"-H"},
            description = "Specifies the headers to be passed with all requests and will override values from the replay files for the same header name")
    Map<String, Object> headersMap = Maps.newHashMap();

    @Inject
    public ReplayCommand(ServiceCaller serviceCaller, CatsDSLParser catsDSLParser) {
        this.serviceCaller = serviceCaller;
        this.catsDSLParser = catsDSLParser;
    }

    public List<String> parseTestCases() {
        return Optional.of(Arrays.asList(tests)).orElse(Collections.emptyList()).stream()
                .map(testCase -> testCase.trim().strip())
                .map(testCase -> testCase.endsWith(".json") ? testCase : "cats-report/" + testCase + ".json")
                .toList();
    }

    public void executeTestCase(String testCaseFileName) throws IOException {
        String testCaseFile = Files.readString(Paths.get(testCaseFileName));
        logger.note("Loaded content: \n" + testCaseFile);
        CatsTestCase testCase = JsonUtils.GSON.fromJson(testCaseFile, CatsTestCase.class);
        logger.start("Calling service endpoint: {}", testCase.getRequest().getUrl());
        List<KeyValuePair<String, Object>> headersFromFile = new java.util.ArrayList<>(Optional.ofNullable(testCase.getRequest().getHeaders()).orElse(Collections.emptyList()));
        headersFromFile.removeIf(header -> headersMap.containsKey(header.getKey()));
        headersFromFile.addAll(headersMap.entrySet().stream().map(entry -> new KeyValuePair<>(entry.getKey(), entry.getValue())).toList());

        headersFromFile.forEach(header -> header.setValue(catsDSLParser.parseAndGetResult(header.getValue().toString(), null)));
        CatsResponse response = serviceCaller.callService(testCase.getRequest(), Collections.emptySet());
        String responseBody = JsonUtils.GSON.toJson(response.getBody().isBlank() ? "empty response" : response.getJsonBody());

        logger.complete("Response body: \n{}", responseBody);
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
