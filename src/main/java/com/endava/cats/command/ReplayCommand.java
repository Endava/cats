package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.dsl.impl.EnvVariableParser;
import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.CustomFuzzerUtil;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This will replay a given list of tests solely based on the information received in the test case file(s).
 */
@CommandLine.Command(
        name = "replay",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Replay previously executed CATS tests",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Replay Test 1 from the default reporting folder:",
                "    cats replay Test1",
                "", "  Replay Test 42 statefully (re-running upstream 2XX producer steps first to mint fresh IDs):",
                "    cats replay Test42 --stateful",
                "", "  Export Test 42 and its upstream producer chain as a reusable --functionalFlow YAML file:",
                "    cats replay Test42 --export-flow reproducer-flow.yml",
                "", "  Retry all failed (error) tests from the default cats-report folder:",
                "    cats replay --errors"},
        versionProvider = VersionProvider.class)
@Unremovable
public class ReplayCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\$\\w+");
    private static final Pattern TEST_FILE_PATTERN = Pattern.compile("^Test(\\d+)\\.json$");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ReplayCommand.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private int exitCode = CommandLine.ExitCode.OK;

    @CommandLine.Parameters(
            description = "The list of CATS tests. When providing a .json extension it will be considered a path, " +
                    "otherwise it will look for that test in the cats-report folder. Not required when using --errors or --warnings", split = ",", arity = "0..")
    String[] tests;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    ApiArguments apiArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArguments;

    @CommandLine.Option(names = {"-D", "--debug"},
            description = "Set CATS log level to ALL. Useful for diagnosing when raising bugs")
    private boolean debug;

    @CommandLine.Option(names = {"-v", "--verbose"},
            description = "More verbose output")
    private boolean verbose;

    @CommandLine.Option(names = {"-H"},
            description = "Specifies the headers to be passed with all the re-played tests. It will override values from the replay files for the same header name")
    Map<String, Object> headersMap = new HashMap<>();

    @CommandLine.Option(names = {"-s", "--server"},
            description = "Base URL of the service. It can be used to overwrite the base URL from the initial test in order to replay it against other service instances")
    private String server;

    @CommandLine.Option(names = {"--callTimeout"},
            description = "Maximum total time in seconds for an HTTP call. Use 0 to disable. Default: @|bold,underline ${DEFAULT-VALUE}|@",
            defaultValue = "20")
    private int callTimeout = 20;

    @CommandLine.Option(names = {"--maxResponseBytes"},
            description = "Maximum number of decompressed response-body bytes to capture. Use 0 to disable. Default: @|bold,underline ${DEFAULT-VALUE}|@",
            defaultValue = "10485760")
    private long maxResponseBytes = 10485760;

    @CommandLine.Option(names = {"-o", "--output"},
            description = "If supplied, it will create TestXXX.json files within the given folder with the updated responses received when replaying the tests")
    private String outputReportFolder;

    @CommandLine.Option(names = {"--errors"},
            description = "Retry all tests with error results from the cats-summary-report.json in the report folder")
    private boolean errors;

    @CommandLine.Option(names = {"--warnings"},
            description = "Retry all tests with warning results from the cats-summary-report.json in the report folder. Use together with --errors")
    private boolean warnings;

    @CommandLine.Option(names = {"--reportFolder", "-r"},
            description = "The folder containing the cats-summary-report.json file when using --errors/--warnings. Default: @|bold,underline cats-report|@")
    private String reportFolder = "cats-report";

    @CommandLine.Option(names = {"--curl"},
            description = "Print equivalent curl command(s) for the selected test case(s) without executing HTTP calls")
    private boolean curl;

    @CommandLine.Option(names = {"--stateful"},
            description = "Automatically replay upstream 2XX producer test(s) from the report folder first, extract fresh resource IDs from the live responses, and substitute them into the replayed request")
    boolean stateful;

    @CommandLine.Option(names = {"--export-flow"},
            description = "Export the selected test(s) and their upstream producer chain as a runnable --functionalFlow YAML file without executing HTTP calls")
    File exportFlowFile;

    record ProducerLink(CatsTestCase producerTest, String sourceSelector, String oldValue, String variableName) {
    }

    /**
     * Constructs a new instance of the {@code ReplayCommand} class.
     *
     * @param serviceCaller    the service caller used for invoking services during replay
     * @param testCaseListener the test case listener used for handling test case events during replay
     */
    @Inject
    public ReplayCommand(ServiceCaller serviceCaller, TestCaseListener testCaseListener) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
    }

    private List<String> parseTestCases(ReplayStats stats) {
        List<String> testCaseFiles = new ArrayList<>();
        if (stats != null) {
            testCaseFiles.addAll(loadTestIdsAndInitialStats(stats));
        }
        if (tests != null && tests.length > 0) {
            testCaseFiles.addAll(Arrays.stream(tests)
                    .map(testCase -> testCase.trim().strip())
                    .map(testCase -> testCase.endsWith(".json") ? testCase : reportFolder + "/" + testCase + ".json")
                    .toList());
        }
        return testCaseFiles;
    }

    private List<String> loadTestIdsAndInitialStats(ReplayStats stats) {
        Path summaryPath = Paths.get(reportFolder, "cats-summary-report.json");
        if (!Files.exists(summaryPath)) {
            logger.error("Summary report not found at: {}", summaryPath);
            return Collections.emptyList();
        }

        try {
            String content = Files.readString(summaryPath);
            SummaryReport report = JsonUtils.GSON.fromJson(content, SummaryReport.class);
            if (report == null || report.testCases == null) {
                logger.error("Invalid summary report format");
                return Collections.emptyList();
            }

            List<String> selectedTests = new ArrayList<>();
            for (TestCaseSummaryEntry entry : report.testCases) {
                countInitialResult(entry.result, stats);
                if (entry.id != null && shouldRetryTest(entry.result)) {
                    selectedTests.add(reportFolder + "/" + entry.id.replace(" ", "") + ".json");
                }
            }
            if (selectedTests.isEmpty()) {
                logger.info("No failed tests found to retry");
            } else {
                logger.info("Found {} failed test(s) to retry", selectedTests.size());
            }
            return Collections.unmodifiableList(selectedTests);
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to read or parse summary report: {}", e.getMessage());
            logger.debug("Stacktrace:", e);
            return Collections.emptyList();
        }
    }

    private void countInitialResult(String result, ReplayStats stats) {
        if (CatsResultFactory.isError(result)) {
            stats.initialErrors++;
        } else if (CatsResultFactory.isWarn(result)) {
            stats.initialWarnings++;
        }
    }

    private boolean shouldRetryTest(String result) {
        return (errors && CatsResultFactory.isError(result)) ||
                (warnings && CatsResultFactory.isWarn(result));
    }

    @RegisterForReflection
    static class SummaryReport {
        List<TestCaseSummaryEntry> testCases;
    }

    @RegisterForReflection
    static class TestCaseSummaryEntry {
        String id;
        String result;
    }

    static class ReplayStats {
        int initialErrors;
        int initialWarnings;
        int unchanged;
        int improved;
        int regressed;
    }

    private void executeTestCase(String testCaseFileName, ReplayStats stats) throws IOException {
        CatsTestCase testCase = this.loadTestCaseFile(testCaseFileName);
        this.loadHeadersIfSupplied(testCase);
        testCase.getRequest().setUrl(resolveReplayUrl(testCase.getRequest().getUrl()));
        if (curl) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(buildCurlCommand(testCase.getRequest()));
            return;
        }
        if (stateful) {
            Path reportDir = resolveReportDirForTest(testCaseFileName);
            List<ProducerLink> producers = findUpstreamProducerLinks(testCase, reportDir);
            executeStatefulProducersAndSubstitute(testCase, producers);
        }
        logger.start("Calling service endpoint: {}", testCase.getRequest().getUrl());

        CatsResponse response = invokeServiceSafely(testCase.getRequest());
        if (verbose) {
            logger.complete("Response body: \n{}", response.getBody());
        }
        this.writeTestJsonsIfSupplied(testCase, response);
        this.showResponseCodesDifferences(testCase, response);
        this.updateStats(testCase, response, stats);
    }

    private CatsResponse invokeServiceSafely(CatsRequest request) {
        try {
            return serviceCaller.callService(request, Collections.emptySet());
        } catch (IOException e) {
            CatsResponse.ExceptionalResponse exceptionalResponse = CatsResponse.getResponseByException(e);
            return CatsResponse.builder()
                    .jsonBody(JsonUtils.parseAsJsonElement(exceptionalResponse.responseBody()))
                    .body(exceptionalResponse.responseBody())
                    .responseCode(exceptionalResponse.responseCode())
                    .build();
        }
    }

    private Path resolveReportDirForTest(String testCaseFileName) {
        Path p = Paths.get(testCaseFileName).toAbsolutePath().getParent();
        return p != null && Files.isDirectory(p) ? p : Paths.get(reportFolder);
    }

    List<ProducerLink> findUpstreamProducerLinks(CatsTestCase targetTest, Path reportDir) {
        List<CatsTestCase> successfulPosts = loadSuccessfulPostTests(reportDir);
        if (successfulPosts.isEmpty()) {
            return List.of();
        }
        List<ResourceCorrelation> correlations = targetTest.getRequestProvenance() != null
                ? targetTest.getRequestProvenance().getRuntimeCorrelations()
                : List.of();
        List<ProducerLink> links = new ArrayList<>();
        Set<String> seenValues = new LinkedHashSet<>();

        for (ResourceCorrelation corr : correlations) {
            String val = corr.getValue() == null ? "" : String.valueOf(corr.getValue());
            if (!val.isBlank() && seenValues.add(val)) {
                findMatchingProducer(corr.getSourcePath(), val, successfulPosts)
                        .ifPresent(prod -> links.add(new ProducerLink(
                                prod, toFlowSelector(corr.getSourceLocation()), val,
                                sanitizeVarName(corr.getTarget().name()))));
            }
        }
        if (links.isEmpty()) {
            inferFallbackProducerLink(targetTest, successfulPosts).ifPresent(links::add);
        }
        return links;
    }

    private Optional<ProducerLink> inferFallbackProducerLink(CatsTestCase targetTest, List<CatsTestCase> successfulPosts) {
        String contractPath = targetTest.getContractPath();
        if (StringUtils.isBlank(contractPath) || !contractPath.contains("{")) {
            return Optional.empty();
        }
        int braceIdx = contractPath.indexOf('{');
        int closeBraceIdx = contractPath.indexOf('}', braceIdx);
        String paramName = closeBraceIdx > braceIdx + 1
                ? sanitizeVarName(contractPath.substring(braceIdx + 1, closeBraceIdx))
                : "resourceId";
        String parentPath = braceIdx > 1 ? contractPath.substring(0, braceIdx - 1) : "/";
        String urlPath = extractPathFromUrl(targetTest.getRequest().getUrl());
        String concreteVal = extractTrailingSegment(urlPath);
        if (StringUtils.isBlank(concreteVal)) {
            return Optional.empty();
        }
        return findMatchingProducer(parentPath, concreteVal, successfulPosts)
                .map(prod -> new ProducerLink(prod, "id", concreteVal, paramName));
    }

    private static Optional<CatsTestCase> findMatchingProducer(String sourcePath, String targetVal,
                                                               List<CatsTestCase> successfulPosts) {
        for (CatsTestCase candidate : successfulPosts) {
            String candidatePath = Objects.requireNonNullElse(candidate.getContractPath(), candidate.getPath());
            if (sourcePath != null && sourcePath.equalsIgnoreCase(candidatePath)
                    && candidate.getResponse() != null && candidate.getResponse().getBody() != null
                    && candidate.getResponse().getBody().contains(targetVal)) {
                return Optional.of(candidate);
            }
        }
        return successfulPosts.stream()
                .filter(c -> sourcePath != null && sourcePath.equalsIgnoreCase(
                        Objects.requireNonNullElse(c.getContractPath(), c.getPath())))
                .findFirst();
    }

    private static List<CatsTestCase> loadSuccessfulPostTests(Path reportDir) {
        if (reportDir == null || !Files.isDirectory(reportDir)) {
            return List.of();
        }
        List<CatsTestCase> posts = new ArrayList<>();
        try (Stream<Path> stream = Files.list(reportDir)) {
            List<Path> files = stream
                    .filter(p -> TEST_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(ReplayCommand::extractTestNumber))
                    .toList();
            for (Path file : files) {
                CatsTestCase tc = readTestCaseQuietly(file);
                if (isSuccessfulPost(tc)) {
                    posts.add(tc);
                }
            }
        } catch (IOException _) {
            return List.of();
        }
        return posts;
    }

    private static int extractTestNumber(Path file) {
        Matcher m = TEST_FILE_PATTERN.matcher(file.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private static CatsTestCase readTestCaseQuietly(Path file) {
        try {
            return JsonUtils.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CatsTestCase.class);
        } catch (IOException | RuntimeException _) {
            return null;
        }
    }

    private static boolean isSuccessfulPost(CatsTestCase tc) {
        return tc != null && tc.getRequest() != null && tc.getResponse() != null
                && "POST".equalsIgnoreCase(tc.getRequest().getHttpMethod())
                && ResponseCodeFamily.is2xxCode(tc.getResponse().getResponseCode());
    }

    private void executeStatefulProducersAndSubstitute(CatsTestCase targetTest, List<ProducerLink> links) {
        for (ProducerLink link : links) {
            CatsTestCase copy = copyTestCaseRequest(link.producerTest());
            copy.updateServer(server);
            loadHeadersIfSupplied(copy);
            copy.getRequest().setUrl(resolveReplayUrl(copy.getRequest().getUrl()));
            logger.info("Replaying upstream producer [{}] {} {}",
                    link.producerTest().getTestId(), copy.getRequest().getHttpMethod(), copy.getRequest().getUrl());
            CatsResponse liveResp = invokeServiceSafely(copy.getRequest());
            if (ResponseCodeFamily.is2xxCode(liveResp.getResponseCode()) && !liveResp.isBodyTruncated()) {
                String freshVal = CustomFuzzerUtil.extractResponseValue(liveResp, link.sourceSelector());
                if (StringUtils.isNotBlank(freshVal) && !JsonUtils.isNotSet(freshVal)) {
                    replaceCorrelatedValueInRequest(targetTest.getRequest(), link.oldValue(), freshVal);
                }
            }
        }
    }

    private static CatsTestCase copyTestCaseRequest(CatsTestCase source) {
        String serialized = JsonUtils.GSON.toJson(source);
        return JsonUtils.GSON.fromJson(serialized, CatsTestCase.class);
    }

    private static void replaceCorrelatedValueInRequest(CatsRequest request, String oldValue, String newValue) {
        if (StringUtils.isBlank(oldValue) || Objects.equals(oldValue, newValue)) {
            return;
        }
        if (request.getUrl() != null) {
            request.setUrl(request.getUrl().replace(oldValue, newValue));
        }
        if (request.getPayload() != null) {
            request.setPayload(request.getPayload().replace(oldValue, newValue));
        }
    }

    String buildFunctionalFlowYaml(List<String> testCaseFiles) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by `cats replay --export-flow`\n");
        sb.append("steps:\n");
        Set<String> emittedProducerIds = new LinkedHashSet<>();

        for (String fileName : testCaseFiles) {
            CatsTestCase target = loadTestCaseFile(fileName);
            Path reportDir = resolveReportDirForTest(fileName);
            List<ProducerLink> links = findUpstreamProducerLinks(target, reportDir);
            for (ProducerLink link : links) {
                String prodId = StringUtils.defaultIfBlank(link.producerTest().getTestId(), "producer");
                if (emittedProducerIds.add(prodId + ":" + link.variableName())) {
                    appendProducerFlowStep(sb, link);
                }
            }
            appendTargetFlowStep(sb, target, links);
        }
        return sb.toString();
    }

    private static void appendProducerFlowStep(StringBuilder sb, ProducerLink link) {
        CatsTestCase prod = link.producerTest();
        String method = StringUtils.defaultIfBlank(prod.getRequest().getHttpMethod(), "POST").toUpperCase(Locale.ROOT);
        String path = extractPathFromUrl(prod.getRequest().getUrl());
        String stepId = sanitizeVarName(StringUtils.defaultIfBlank(prod.getTestId(), "seedResource"));
        sb.append("  - name: \"").append(escapeYaml(stepId)).append("\"\n");
        sb.append("    description: \"Prerequisite producer from ").append(escapeYaml(StringUtils.defaultIfBlank(prod.getTestId(), "report"))).append("\"\n");
        sb.append("    ").append(method).append(": \"").append(escapeYaml(path)).append("\"\n");
        sb.append("    base: empty\n");
        appendStepBodyIfPresent(sb, prod.getRequest().getPayload());
        sb.append("    save:\n");
        sb.append("      ").append(link.variableName()).append(": \"").append(escapeYaml(link.sourceSelector())).append("\"\n");
        sb.append("    expect:\n");
        sb.append("      status: 2XX\n\n");
    }

    private static void appendTargetFlowStep(StringBuilder sb, CatsTestCase target, List<ProducerLink> links) {
        String method = StringUtils.defaultIfBlank(target.getRequest().getHttpMethod(), "GET").toUpperCase(Locale.ROOT);
        String urlPath = extractPathFromUrl(target.getRequest().getUrl());
        String payload = Optional.ofNullable(target.getRequest().getPayload()).orElse("");
        for (ProducerLink link : links) {
            String placeholder = "${" + link.variableName() + "}";
            urlPath = urlPath.replace(link.oldValue(), placeholder);
            payload = payload.replace(link.oldValue(), placeholder);
        }
        String testId = StringUtils.defaultIfBlank(target.getTestId(), "reproducer");
        String desc = StringUtils.defaultIfBlank(target.getScenario(), "Reproduce " + testId);
        sb.append("  - name: \"").append(escapeYaml(sanitizeVarName(testId))).append("\"\n");
        sb.append("    description: \"").append(escapeYaml(desc)).append("\"\n");
        sb.append("    ").append(method).append(": \"").append(escapeYaml(urlPath)).append("\"\n");
        sb.append("    base: empty\n");
        appendStepBodyIfPresent(sb, payload);
        sb.append("    expect:\n");
        int origCode = target.getResponse() != null ? target.getResponse().getResponseCode() : 200;
        if (ResponseCodeFamily.is5xxCode(origCode)) {
            sb.append("      status: [200, 201, 400, 422]\n");
            sb.append("      that:\n");
            sb.append("        - \"status < 500\"\n");
        } else {
            sb.append("      status: ").append(origCode > 0 ? origCode : "2XX").append("\n");
        }
    }

    private static void appendStepBodyIfPresent(StringBuilder sb, String payload) {
        if (StringUtils.isNotBlank(payload) && !"{}".equals(payload.trim())) {
            sb.append("    body: \"").append(escapeYaml(payload)).append("\"\n");
        }
    }

    private static String toFlowSelector(String sourceLocation) {
        if (StringUtils.isBlank(sourceLocation)) {
            return "id";
        }
        if (sourceLocation.startsWith("response.body.$.")) {
            return sourceLocation.substring("response.body.$.".length());
        }
        if (sourceLocation.startsWith("response.header.")) {
            return "header#" + sourceLocation.substring("response.header.".length());
        }
        return "id";
    }

    private static String sanitizeVarName(String raw) {
        String clean = StringUtils.defaultIfBlank(raw, "resourceId").replaceAll("[^A-Za-z0-9_]", "");
        return clean.isEmpty() ? "resourceId" : clean;
    }

    private static String extractPathFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "/";
        }
        try {
            URI uri = URI.create(url);
            String path = StringUtils.defaultIfBlank(uri.getRawPath(), "/");
            return uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
        } catch (RuntimeException _) {
            return url;
        }
    }

    private static String extractTrailingSegment(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        String clean = StringUtils.stripEnd(path, "/");
        int slash = clean.lastIndexOf('/');
        return slash >= 0 ? clean.substring(slash + 1) : clean;
    }

    private static String escapeYaml(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void updateStats(CatsTestCase testCase, CatsResponse response, ReplayStats stats) {
        if (stats == null) {
            return;
        }
        int oldCode = testCase.getResponse().getResponseCode();
        int newCode = response.getResponseCode();

        boolean wasError = ResponseCodeFamily.is5xxCode(oldCode) || oldCode == 0;
        boolean isNowError = ResponseCodeFamily.is5xxCode(newCode) || newCode == 0;
        boolean isNowClientError = ResponseCodeFamily.is4xxCode(newCode);
        boolean isNowSuccess = ResponseCodeFamily.is2xxCode(newCode);

        if (oldCode == newCode) {
            stats.unchanged++;
        } else if (isNowSuccess || (wasError && isNowClientError)) {
            stats.improved++;
        } else if (isNowError) {
            stats.regressed++;
        }
    }

    void showResponseCodesDifferences(CatsTestCase catsTestCase, CatsResponse response) {
        logger.noFormat("");
        logger.star("Old response code: {}", catsTestCase.getResponse().getResponseCode());
        logger.star("New response code: {}", response.getResponseCode());
        logger.noFormat("");

        if (verbose) {
            logger.star("Old response body: {}", catsTestCase.getResponse().getJsonBody());
            logger.star("New response body: {}", response.getJsonBody());
            logger.noFormat("");
        }
    }

    void writeTestJsonsIfSupplied(CatsTestCase catsTestCase, CatsResponse response) {
        if (StringUtils.isBlank(this.outputReportFolder)) {
            return;
        }

        catsTestCase.setResponse(response);
        testCaseListener.writeIndividualTestCase(catsTestCase);
    }

    private void loadHeadersIfSupplied(CatsTestCase testCase) {
        List<KeyValuePair<String, Object>> headersFromFile = new ArrayList<>(Optional.ofNullable(testCase.getRequest().getHeaders()).orElse(Collections.emptyList()));
        headersFromFile.removeIf(header -> headersMap.containsKey(header.getKey()));
        headersFromFile.addAll(headersMap.entrySet().stream().map(entry -> new KeyValuePair<>(entry.getKey(), entry.getValue())).toList());
        headersFromFile.forEach(header -> header.setValue(resolveReplayValue(header.getValue().toString(), "header " + header.getKey())));
        testCase.getRequest().setHeaders(headersFromFile);
    }

    private String resolveReplayUrl(String url) {
        Matcher matcher = ENV_PLACEHOLDER.matcher(url);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolveReplayValue(matcher.group(), "request URL")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveReplayValue(String value, String source) {
        String resolved = DynamicValueResolver.resolve(value, authArguments.getDynamicVariablesContext());
        if (EnvVariableParser.isMissing(resolved)) {
            String variable = EnvVariableParser.variableName(value.substring(value.indexOf("$$")));
            throw new CatsException("Missing environment variable '" + variable + "' required by replay " + source
                    + ". Set it in the process environment, provide --envFile, or override the header with -H");
        }
        return resolved;
    }

    private CatsTestCase loadTestCaseFile(String testCaseFileName) throws IOException {
        String testCaseFile = Files.readString(Paths.get(testCaseFileName));
        if (verbose) {
            logger.config("Loaded content: \n" + testCaseFile);
        }
        CatsTestCase testCase = JsonUtils.GSON.fromJson(testCaseFile, CatsTestCase.class);
        testCase.updateServer(server);
        return testCase;
    }

    private boolean initReportingPath() {
        if (StringUtils.isBlank(this.outputReportFolder)) {
            return true;
        }

        try {
            testCaseListener.initReportingPath(this.outputReportFolder);
            testCaseListener.writeHelperFiles();
            return true;
        } catch (Exception e) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            logger.error("There was an issue creating the output folder: {}", e.getMessage());
            logger.debug("Stacktrace:", e);
            return false;
        }
    }

    @Override
    public void run() {
        exitCode = CommandLine.ExitCode.OK;
        if (debug) {
            CatsUtil.setCatsLogLevel("ALL");
            logger.fav("Setting CATS log level to ALL!");
        }
        apiArguments.setCallTimeout(callTimeout);
        apiArguments.setMaxResponseBytes(maxResponseBytes);
        apiArguments.validateResponseLimits(spec);
        authArguments.getEnvironmentVariables();
        logger.config("Environment file: {}", authArguments.getEnvironmentFileStatus());

        ReplayStats stats = errors || warnings ? new ReplayStats() : null;
        List<String> testCases = parseTestCases(stats);
        if (testCases.isEmpty()) {
            logger.warning("No tests to replay. Provide test names as arguments or use --errors/--warnings");
            return;
        }
        if (exportFlowFile != null) {
            exportSelectedTestsAsFlow(testCases);
            return;
        }
        if (!this.initReportingPath()) {
            return;
        }

        for (String testCaseFileName : testCases) {
            if (!executeSingleReplayTest(testCaseFileName, stats)) {
                return;
            }
        }

        if (stats != null && !curl) {
            printSummary(stats, testCases.size());
        }
    }

    private void exportSelectedTestsAsFlow(List<String> testCases) {
        try {
            String flowYaml = buildFunctionalFlowYaml(testCases);
            Path outPath = exportFlowFile.toPath();
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            Files.writeString(outPath, flowYaml, StandardCharsets.UTF_8);
            logger.complete("Exported stateful functional flow reproducer to {}", outPath.toAbsolutePath());
        } catch (IOException e) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            logger.error("Failed to export functional flow file {}: {}", exportFlowFile, e.getMessage());
        }
    }

    private boolean executeSingleReplayTest(String testCaseFileName, ReplayStats stats) {
        try {
            if (!curl) {
                logger.noFormat("");
                logger.start("Executing {}", testCaseFileName);
            }
            this.executeTestCase(testCaseFileName, stats);
            if (!curl) {
                logger.complete("Finish executing {}", testCaseFileName);
            }
            return true;
        } catch (IOException e) {
            logger.debug("Exception while replaying test!", e);
            logger.error("Something went wrong while replaying {}. If the test name ends with .json it is searched as a full path. " +
                    "If it doesn't have an extension it will be searched in the {} folder. Error message: {}", testCaseFileName, reportFolder, e.toString());
            return true;
        } catch (CatsException e) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            logger.error(e.getMessage());
            logger.debug("Replay environment resolution failed", e);
            return false;
        }
    }

    String buildCurlCommand(CatsRequest request) {
        return request.toCurlCommand();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private void printSummary(ReplayStats stats, int totalReplayed) {
        logger.noFormat("");
        logger.noFormat("─".repeat(60));
        logger.info("Replay Summary");
        logger.noFormat("─".repeat(60));
        logger.star("Total tests replayed: {}", totalReplayed);
        logger.star("Initial errors in report: {}", stats.initialErrors);
        logger.star("Initial warnings in report: {}", stats.initialWarnings);
        logger.noFormat("");
        logger.star("Unchanged (same response code): {}", stats.unchanged);
        logger.complete("Improved (better response): {}", stats.improved);
        if (stats.regressed > 0) {
            logger.error("Regressed (worse response): {}", stats.regressed);
        } else {
            logger.star("Regressed (worse response): {}", stats.regressed);
        }
        logger.noFormat("─".repeat(60));
    }
}
