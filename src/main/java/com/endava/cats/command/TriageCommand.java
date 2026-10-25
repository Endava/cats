package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.report.ErrorSimilarityDetector;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import lombok.Getter;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contract-aware root-cause clustering, live fix verification ({@code --verify}), {@code --refData} mining from
 * successful {@code 2XX} exchanges, OpenAPI contract-drift patch generation, and {@code --correlations} synthesis.
 */
@CommandLine.Command(
        name = "triage",
        aliases = {"diagnose", "doctor"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Cluster failing CATS report tests into root causes, verify fixes live against a server, mine working --refData from 2XX exchanges, and generate OpenAPI contract patches and --correlations rules.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Report triaged (and all verified 5XX crashes fixed when --verify is enabled)",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Execution error or unfixed 5XX crashes detected during --verify"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Triage the default cats-report folder and cluster all errors by root cause:",
                "    cats triage",
                "", "  Replay 1 representative test per cluster against a live server to verify fixes:",
                "    cats triage -r cats-report --verify -s http://localhost:8080",
                "", "  Mine a ready-to-use --refData file from 2XX exchanges and synthesize --correlations rules:",
                "    cats triage -r cats-report --export-refdata refData.yml --export-correlations correlations.yml",
                "", "  Generate an OpenAPI contract patch for undocumented status codes observed during the run:",
                "    cats triage -r cats-report -c openapi.yml --export-contract-patch contract-patch.yml"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class TriageCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private static final Pattern TEST_FILE_PATTERN = Pattern.compile("Test(\\d+)\\.json$");
    private static final Pattern QUOTED_FIELD_PATTERN = Pattern.compile("['\"]([A-Za-z0-9_.#$-]+)['\"]");
    private static final Pattern ENUM_LIKE_CONSTANT_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final Set<String> DISCRIMINATOR_KEY_SUFFIXES = Set.of(
            "type", "kind", "mode", "category", "channel", "method", "operator", "status", "action", "role", "state");

    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-r", "--report"}, defaultValue = "cats-report",
            description = "The CATS report folder containing Test*.json files (default: ${DEFAULT-VALUE}).")
    String reportFolder = "cats-report";

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "Optional OpenAPI contract used to detect undocumented status codes and response drift.")
    String contractPath;

    @CommandLine.Option(names = {"-s", "--server"},
            description = "Optional target server base URL override when running --verify.")
    String serverUrl;

    @CommandLine.Option(names = {"-H", "--header"},
            description = "Optional header(s) (name=value) to override when replaying clusters with --verify.")
    Map<String, String> customHeaders = new LinkedHashMap<>();

    @CommandLine.Option(names = {"--verify"},
            description = "Replay 1 representative request per root-cause cluster against the server and report FIXED vs STILL_FAILING.")
    boolean verify;

    @CommandLine.Option(names = {"--include-warnings"},
            description = "Include warning test cases in root-cause clustering in addition to error test cases.")
    boolean includeWarnings;

    @CommandLine.Option(names = {"--export-correlations"},
            description = "Write synthesized --correlations YAML rules inferred from 4XX validation errors to the given file.")
    String exportCorrelationsFile;

    @CommandLine.Option(names = {"--export-refdata"},
            description = "Mine working path parameters and resource IDs from 2XX exchanges in the report and write a ready-to-use --refData YAML file.")
    String exportRefDataFile;

    @CommandLine.Option(names = {"--export-contract-patch"},
            description = "Write an OpenAPI paths overlay YAML containing undocumented status codes observed during the run.")
    String exportContractPatchFile;

    @CommandLine.Option(names = {"--export-reproducers"},
            description = "Write a shell script with one minimal curl reproducer per root-cause cluster.")
    String exportReproducersFile;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the triage report as JSON.")
    boolean json;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private TriageReport lastReport;

    @Override
    public void run() {
        try {
            Path folder = Paths.get(reportFolder);
            if (!Files.isDirectory(folder)) {
                throw new IllegalArgumentException("Report directory does not exist: " + folder);
            }
            List<IndexedTestCase> testCases = loadTestCases(folder);
            OpenAPI openAPI = loadOpenApiIfSupplied();
            lastReport = buildTriageReport(folder.toString(), testCases, openAPI);
            if (StringUtils.isNotBlank(exportCorrelationsFile)) {
                Files.writeString(Paths.get(exportCorrelationsFile), lastReport.synthesizedCorrelationsYaml(), StandardCharsets.UTF_8);
            }
            if (StringUtils.isNotBlank(exportRefDataFile)) {
                Files.writeString(Paths.get(exportRefDataFile), lastReport.minedRefDataYaml(), StandardCharsets.UTF_8);
            }
            if (StringUtils.isNotBlank(exportContractPatchFile)) {
                Files.writeString(Paths.get(exportContractPatchFile), lastReport.contractPatchYaml(), StandardCharsets.UTF_8);
            }
            if (StringUtils.isNotBlank(exportReproducersFile)) {
                Files.writeString(Paths.get(exportReproducersFile), buildReproducersScript(lastReport), StandardCharsets.UTF_8);
            }
            renderReport(lastReport);
            exitCode = computeExitCode(lastReport);
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to triage CATS report: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }

    private OpenAPI loadOpenApiIfSupplied() {
        if (StringUtils.isBlank(contractPath)) {
            return null;
        }
        try {
            return OpenApiUtils.readOpenApi(contractPath);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read contract {}: {}", contractPath, e.getMessage());
            return null;
        }
    }

    private int computeExitCode(TriageReport report) {
        if (!verify) {
            return CommandLine.ExitCode.OK;
        }
        boolean anyUnfixedCrash = report.clusters().stream()
                .anyMatch(c -> "SERVER_CRASH_5XX".equals(c.category()) && !"FIXED".equals(c.verificationStatus()));
        return anyUnfixedCrash ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
    }

    List<IndexedTestCase> loadTestCases(Path folder) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream
                    .filter(p -> TEST_FILE_PATTERN.matcher(p.getFileName().toString()).find())
                    .sorted(Comparator.comparingInt(TriageCommand::extractTestNumber))
                    .toList();
        }
        List<IndexedTestCase> result = new ArrayList<>(files.size());
        for (Path file : files) {
            CatsTestCase.readTestCaseFile(file)
                    .filter(tc -> tc.getRequest() != null && tc.getResponse() != null)
                    .ifPresent(tc -> result.add(new IndexedTestCase(extractTestNumber(file), tc)));
        }
        return result;
    }

    private static int extractTestNumber(Path path) {
        Matcher m = TEST_FILE_PATTERN.matcher(path.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    TriageReport buildTriageReport(String folderPath, List<IndexedTestCase> allTests) {
        return buildTriageReport(folderPath, allTests, null);
    }

    TriageReport buildTriageReport(String folderPath, List<IndexedTestCase> allTests, OpenAPI openAPI) {
        Set<String> pathsWithSuccessfulPost = new LinkedHashSet<>();
        Map<String, Integer> firstDeleteTestByPath = new LinkedHashMap<>();
        Map<String, Map<String, Object>> minedRefData = new LinkedHashMap<>();
        Map<String, Map<String, Set<Integer>>> undocumentedCodesByPathAndMethod = new TreeMap<>();
        int errorCount = 0;
        int warningCount = 0;

        for (IndexedTestCase indexed : allTests) {
            CatsTestCase tc = indexed.testCase();
            int code = tc.getResponse().getResponseCode();
            String method = Objects.requireNonNullElse(tc.getRequest().getHttpMethod(), "").toUpperCase(Locale.ROOT);
            String path = Objects.requireNonNullElse(tc.getContractPath(), Objects.requireNonNullElse(tc.getPath(), ""));
            if (ResponseCodeFamily.is2xxCode(code)) {
                if ("POST".equals(method)) {
                    pathsWithSuccessfulPost.add(path);
                } else if ("DELETE".equals(method)) {
                    firstDeleteTestByPath.putIfAbsent(path, indexed.number());
                }
                mineSuccessfulExchangeForRefData(tc, path, minedRefData);
            }
            recordContractDriftIfUndocumented(tc, path, method, code, openAPI, undocumentedCodesByPathAndMethod);
            if ("error".equalsIgnoreCase(tc.getResult())) {
                errorCount++;
            } else if ("warn".equalsIgnoreCase(tc.getResult()) || "warning".equalsIgnoreCase(tc.getResult())) {
                warningCount++;
            }
        }

        List<ClusterAccumulator> accumulators = new ArrayList<>();
        List<SynthesizedCorrelationRule> correlationRules = new ArrayList<>();
        Set<String> seenRuleKeys = new LinkedHashSet<>();

        for (IndexedTestCase indexed : allTests) {
            CatsTestCase tc = indexed.testCase();
            boolean isError = "error".equalsIgnoreCase(tc.getResult());
            boolean isWarn = "warn".equalsIgnoreCase(tc.getResult()) || "warning".equalsIgnoreCase(tc.getResult());
            if (!isError && (!includeWarnings || !isWarn)) {
                continue;
            }
            ClusterClassification classification = classify(indexed, pathsWithSuccessfulPost, firstDeleteTestByPath, minedRefData);
            ClusterAccumulator matching = findMatchingAccumulator(accumulators, classification);
            if (matching == null) {
                matching = new ClusterAccumulator(classification, buildCurlReproducer(tc), tc);
                accumulators.add(matching);
            }
            matching.add(tc.getTestId(), tc.getFuzzer());
            maybeSynthesizeCorrelationRule(tc, correlationRules, seenRuleKeys);
        }

        OkHttpClient verifyClient = verify ? new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .followRedirects(false)
                .build() : null;

        List<RootCauseCluster> clusters = accumulators.stream()
                .map(acc -> acc.toCluster(verifyClient, serverUrl, customHeaders))
                .sorted(Comparator.comparingInt(RootCauseCluster::severityRank)
                        .thenComparing(Comparator.comparingInt(RootCauseCluster::occurrences).reversed()))
                .toList();

        int triagedCount = errorCount + (includeWarnings ? warningCount : 0);
        double noiseReductionPct = triagedCount == 0 || clusters.isEmpty()
                ? 0.0
                : Math.max(0.0, 100.0 * (triagedCount - clusters.size()) / triagedCount);

        String correlationsYaml = renderCorrelationsYaml(correlationRules);
        String refDataYaml = renderRefDataYaml(minedRefData);
        String contractPatchYaml = renderContractPatchYaml(undocumentedCodesByPathAndMethod);
        return new TriageReport(
                folderPath,
                allTests.size(),
                errorCount,
                warningCount,
                clusters.size(),
                Math.round(noiseReductionPct * 10.0) / 10.0,
                clusters,
                correlationRules,
                correlationsYaml,
                refDataYaml,
                contractPatchYaml);
    }

    private static ClusterAccumulator findMatchingAccumulator(List<ClusterAccumulator> accumulators,
                                                              ClusterClassification candidate) {
        for (ClusterAccumulator existing : accumulators) {
            ClusterClassification c = existing.classification;
            if (c. clusterKey().equals(candidate.clusterKey())) {
                return existing;
            }
            if (c.category().equals(candidate.category())
                    && c.operation().equals(candidate.operation())
                    && c.statusCode() == candidate.statusCode()
                    && ErrorSimilarityDetector.areErrorsSimilar(c.diagnosis(), candidate.diagnosis())) {
                return existing;
            }
        }
        return null;
    }

    private static void mineSuccessfulExchangeForRefData(CatsTestCase tc, String contractPath,
                                                         Map<String, Map<String, Object>> minedRefData) {
        if (StringUtils.isBlank(contractPath)) {
            return;
        }
        String url = Objects.requireNonNullElse(tc.getRequest().getUrl(), tc.getFullRequestPath());
        if (StringUtils.isNotBlank(url) && contractPath.contains("{")) {
            Map<String, String> extractedParams = extractConcretePathParams(contractPath, url);
            if (!extractedParams.isEmpty()) {
                Map<String, Object> pathMap = minedRefData.computeIfAbsent(contractPath, _ -> new LinkedHashMap<>());
                extractedParams.forEach(pathMap::putIfAbsent);
            }
        }
        JsonElement respJson = parseResponseJson(tc);
        if (respJson != null && respJson.isJsonObject()) {
            JsonObject obj = respJson.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String k = entry.getKey();
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive() && isIdKey(k)) {
                    Object val = primitiveToJavaObject(entry.getValue());
                    String itemPath = contractPath.endsWith("}") ? contractPath : contractPath + "/{" + k + "}";
                    minedRefData.computeIfAbsent(itemPath, _ -> new LinkedHashMap<>()).putIfAbsent(k, val);
                }
            }
        }
    }

    private static Map<String, String> extractConcretePathParams(String contractPath, String fullUrl) {
        Map<String, String> params = new LinkedHashMap<>();
        try {
            String urlPath = URI.create(fullUrl).getPath();
            if (urlPath == null) {
                return params;
            }
            String[] contractSegs = contractPath.split("/");
            String[] actualSegs = urlPath.split("/");
            if (contractSegs.length <= actualSegs.length) {
                int offset = actualSegs.length - contractSegs.length;
                for (int i = 0; i < contractSegs.length; i++) {
                    String cSeg = contractSegs[i];
                    if (cSeg.startsWith("{") && cSeg.endsWith("}")) {
                        String paramName = cSeg.substring(1, cSeg.length() - 1);
                        String val = actualSegs[i + offset];
                        if (StringUtils.isNotBlank(val)) {
                            params.put(paramName, val);
                        }
                    }
                }
            }
        } catch (RuntimeException _) {
            // ignore malformed URL
        }
        return params;
    }

    private static boolean isIdKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return "id".equals(lower) || "uuid".equals(lower) || "guid".equals(lower) || lower.endsWith("id");
    }

    private static Object primitiveToJavaObject(JsonElement el) {
        if (el.getAsJsonPrimitive().isNumber()) {
            return el.getAsJsonPrimitive().getAsNumber();
        }
        if (el.getAsJsonPrimitive().isBoolean()) {
            return el.getAsJsonPrimitive().getAsBoolean();
        }
        return el.getAsString();
    }

    private static void recordContractDriftIfUndocumented(CatsTestCase tc, String path, String method, int code,
                                                          OpenAPI openAPI,
                                                          Map<String, Map<String, Set<Integer>>> driftMap) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(method) || code < 200 || code >= 500) {
            return;
        }
        boolean undocumented = false;
        if (openAPI != null && openAPI.getPaths() != null) {
            PathItem pathItem = openAPI.getPaths().get(path);
            if (pathItem != null) {
                Operation op = HttpMethod.fromString(method).map(hm -> HttpMethod.getOperation(hm, pathItem)).orElse(null);
                if (op != null && op.getResponses() != null) {
                    String codeStr = String.valueOf(code);
                    String familyStr = (code / 100) + "XX";
                    undocumented = op.getResponses().keySet().stream()
                            .noneMatch(k -> k.equalsIgnoreCase(codeStr)
                                    || k.equalsIgnoreCase(familyStr)
                                    || "default".equalsIgnoreCase(k));
                }
            }
        } else if (tc.getResultReason() != null && tc.getResultReason().toLowerCase(Locale.ROOT).contains("undocumented")) {
            undocumented = true;
        } else if (tc.getResultDetails() != null && tc.getResultDetails().toLowerCase(Locale.ROOT).contains("undocumented")) {
            undocumented = true;
        }
        if (undocumented) {
            driftMap.computeIfAbsent(path, _ -> new TreeMap<>())
                    .computeIfAbsent(method.toLowerCase(Locale.ROOT), _ -> new TreeSet<>())
                    .add(code);
        }
    }

    private ClusterClassification classify(IndexedTestCase indexed,
                                           Set<String> pathsWithSuccessfulPost,
                                           Map<String, Integer> firstDeleteTestByPath,
                                           Map<String, Map<String, Object>> minedRefData) {
        CatsTestCase tc = indexed.testCase();
        int code = tc.getResponse().getResponseCode();
        String method = Objects.requireNonNullElse(tc.getRequest().getHttpMethod(), "").toUpperCase(Locale.ROOT);
        String path = Objects.requireNonNullElse(tc.getContractPath(), Objects.requireNonNullElse(tc.getPath(), ""));
        String errorSig = extractErrorSignature(tc);
        String clusterNormKey = extractNormalizedErrorKey(tc);
        List<ResourceCorrelation> correlations = tc.getRequestProvenance() != null
                ? tc.getRequestProvenance().getRuntimeCorrelations()
                : List.of();

        for (ResourceCorrelation corr : correlations) {
            if (corr.getTarget().location() == RequestTarget.Location.HEADER) {
                String headerName = corr.getTarget().name();
                String key = "HEADER_CORRELATION_COLLISION|" + method + "|" + path + "|" + headerName;
                return new ClusterClassification(
                        key,
                        "HEADER_CORRELATION_COLLISION",
                        1,
                        method + " " + path,
                        code,
                        "Header [" + headerName + "] was overwritten by runtime correlation from [" + corr.getSourceLocation() + "]",
                        "Exclude per-request header '" + headerName + "' from resource pool correlation or pin it via -H / headers.yml.");
            }
        }

        if (ResponseCodeFamily.is5xxCode(code)) {
            String key = "SERVER_CRASH_5XX|" + method + "|" + path + "|" + clusterNormKey;
            return new ClusterClassification(
                    key,
                    "SERVER_CRASH_5XX",
                    0,
                    method + " " + path,
                    code,
                    "Unhandled " + code + " server error (" + errorSig + ")",
                    "Inspect server stacktrace for " + method + " " + path + " and verify your fix with `cats triage --verify`.");
        }

        if ((code == 401 || code == 403) && !ConsoleUtils.matchesFuzzer(tc.getFuzzer(), BypassAuthenticationFuzzer.class)) {
            String key = "AUTH_CONFIGURATION_GAP|" + method + "|" + path + "|" + code;
            return new ClusterClassification(
                    key,
                    "AUTH_CONFIGURATION_GAP",
                    2,
                    method + " " + path,
                    code,
                    "Endpoint rejected credentials with HTTP " + code + " (" + errorSig + ")",
                    "Pass valid credentials via -H 'Authorization=Bearer ...' or --authConfig auth.yml.");
        }

        if (code == 404) {
            for (ResourceCorrelation corr : correlations) {
                if (corr.getTarget().location() == RequestTarget.Location.PATH
                        && corr.getSourceLocation() != null && corr.getSourceLocation().endsWith(".name")) {
                    String key = "SUSPICIOUS_CORRELATION_404|" + method + "|" + path + "|" + corr.getSourceLocation();
                    return new ClusterClassification(
                            key,
                            "SUSPICIOUS_CORRELATION_404",
                            2,
                            method + " " + path,
                            code,
                            "Path parameter [" + corr.getTarget().name() + "] was correlated from non-ID field ["
                                    + corr.getSourceLocation() + " = " + corr.getValue() + "]",
                            "Ensure collection responses expose a primary resource identifier or map '" + corr.getTarget().name() + "' via --refData.");
                }
            }
            String parentDeletePath = findPrecedingDeletePath(path, indexed.number(), firstDeleteTestByPath);
            if (parentDeletePath != null) {
                String key = "RESOURCE_POOL_DEPLETED_404|" + method + "|" + path + "|after:" + parentDeletePath;
                return new ClusterClassification(
                        key,
                        "RESOURCE_POOL_DEPLETED_404",
                        2,
                        method + " " + path,
                        code,
                        "Resource pool depleted after earlier DELETE on [" + parentDeletePath + "] (starting at Test "
                                + firstDeleteTestByPath.get(parentDeletePath) + ")",
                        "Run non-DELETE operations across child paths before parent DELETEs or pass --export-refdata refData.yml.");
            }
            String collectionPath = parentCollectionPath(path);
            boolean producerSucceeded = pathsWithSuccessfulPost.contains(collectionPath);
            String key = "UNRESOLVED_PATH_PARAM_404|" + method + "|" + path;
            String diagnosis = producerSucceeded
                    ? "Path parameter on " + path + " was not enriched with an active resource ID"
                    : "No successful 2XX POST producer recorded for [" + collectionPath + "]";
            String remediation = minedRefData.containsKey(path)
                    ? "Run `cats triage --export-refdata refData.yml` to pin the working IDs mined from 2XX responses: " + minedRefData.get(path)
                    : "Seed a valid resource via POST " + collectionPath + " or pass --urlParams <param>:<value>.";
            return new ClusterClassification(
                    key,
                    "UNRESOLVED_PATH_PARAM_404",
                    2,
                    method + " " + path,
                    code,
                    diagnosis,
                    remediation);
        }

        if ((code == 400 || code == 422) && (ConsoleUtils.matchesFuzzer(tc.getFuzzer(), HappyPathFuzzer.class)
                || ConsoleUtils.matchesFuzzer(tc.getFuzzer(), OnlyRequiredFieldsFuzzer.class)
                || tc.getExpectedResult() != null && tc.getExpectedResult().contains("2XX"))) {
            String key = "CROSS_FIELD_CONSTRAINT_4XX|" + method + "|" + path + "|" + clusterNormKey;
            return new ClusterClassification(
                    key,
                    "CROSS_FIELD_CONSTRAINT_4XX",
                    1,
                    method + " " + path,
                    code,
                    "Domain / cross-field validation rejected request (" + errorSig + ")",
                    "Add the synthesized --correlations rule (`--export-correlations correlations.yml`) for " + path + ".");
        }

        String reason = StringUtils.defaultIfBlank(tc.getResultReason(), "Unexpected response " + code);
        String key = "CONTRACT_MISMATCH|" + method + "|" + path + "|" + code + "|" + reason;
        return new ClusterClassification(
                key,
                "CONTRACT_MISMATCH",
                3,
                method + " " + path,
                code,
                reason + " (HTTP " + code + "; " + errorSig + ")",
                "Run `cats triage --export-contract-patch contract-patch.yml` to align the OpenAPI response specification for " + method + " " + path + ".");
    }

    private static String findPrecedingDeletePath(String path, int testNumber, Map<String, Integer> firstDeleteTestByPath) {
        for (Map.Entry<String, Integer> entry : firstDeleteTestByPath.entrySet()) {
            if (entry.getValue() < testNumber && (path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/"))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String parentCollectionPath(String path) {
        int braceIdx = path.indexOf("/{");
        return braceIdx > 0 ? path.substring(0, braceIdx) : path;
    }

    private static JsonElement parseResponseJson(CatsTestCase tc) {
        if (tc.getResponse().getJsonBody() != null) {
            return tc.getResponse().getJsonBody();
        }
        String raw = tc.getResponse().getBody();
        if (StringUtils.isNotBlank(raw) && JsonUtils.isValidJson(raw)) {
            return JsonUtils.parseAsJsonElement(raw);
        }
        return null;
    }

    private static String extractErrorSignature(CatsTestCase tc) {
        String rawBody = tc.getResponse().getBody();
        if (StringUtils.isBlank(rawBody) && tc.getResponse().getJsonBody() != null) {
            rawBody = tc.getResponse().getJsonBody().toString();
        }
        String fallback = StringUtils.isNotBlank(tc.getResultReason())
                ? tc.getResultReason()
                : "HTTP " + tc.getResponse().getResponseCode();
        String summary = ErrorSimilarityDetector.extractRootCauseSummary(rawBody, fallback);
        return summary.length() > 90 ? summary.substring(0, 90) + "..." : summary;
    }

    private static String extractNormalizedErrorKey(CatsTestCase tc) {
        String rawBody = tc.getResponse().getBody();
        if (StringUtils.isBlank(rawBody) && tc.getResponse().getJsonBody() != null) {
            rawBody = tc.getResponse().getJsonBody().toString();
        }
        return ErrorSimilarityDetector.extractNormalizedClusterKey(rawBody, tc.getResultReason());
    }

    private static void maybeSynthesizeCorrelationRule(CatsTestCase tc,
                                                       List<SynthesizedCorrelationRule> rules,
                                                       Set<String> seenRuleKeys) {
        int code = tc.getResponse().getResponseCode();
        if (code != 400 && code != 422) {
            return;
        }
        String payload = tc.getRequest().getPayload();
        if (StringUtils.isBlank(payload) || !JsonUtils.isValidJson(payload)) {
            return;
        }
        JsonElement reqEl = JsonUtils.parseAsJsonElement(payload);
        if (!reqEl.isJsonObject()) {
            return;
        }
        String errSig = extractErrorSignature(tc);
        Matcher m = QUOTED_FIELD_PATTERN.matcher(errSig);
        if (!m.find()) {
            return;
        }
        String referencedField = m.group(1);
        JsonObject root = reqEl.getAsJsonObject();
        String discriminatorKey = null;
        String discriminatorVal = null;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String k = entry.getKey();
            if (!k.equals(referencedField) && entry.getValue() != null && entry.getValue().isJsonPrimitive()
                    && isDiscriminatorCandidate(k, entry.getValue().getAsString())) {
                discriminatorKey = k;
                discriminatorVal = entry.getValue().getAsString();
                break;
            }
        }
        if (discriminatorKey == null) {
            return;
        }
        String path = Objects.requireNonNullElse(tc.getContractPath(), Objects.requireNonNullElse(tc.getPath(), ""));
        String lowerSig = errSig.toLowerCase(Locale.ROOT);
        String action = (lowerSig.contains("must be null") || lowerSig.contains("not allowed")
                || lowerSig.contains("forbidden") || lowerSig.contains("unsupported"))
                ? "REMOVE"
                : "REQUIRE";
        String dedupKey = path + "|" + discriminatorKey + "=" + discriminatorVal + "|" + action + "|" + referencedField;
        if (seenRuleKeys.add(dedupKey)) {
            rules.add(new SynthesizedCorrelationRule(path, discriminatorKey, discriminatorVal, action, referencedField, errSig));
        }
    }

    private static boolean isDiscriminatorCandidate(String fieldName, String fieldValue) {
        if (StringUtils.isBlank(fieldName) || StringUtils.isBlank(fieldValue)) {
            return false;
        }
        String lowerName = fieldName.toLowerCase(Locale.ROOT);
        if (DISCRIMINATOR_KEY_SUFFIXES.stream().anyMatch(lowerName::endsWith)) {
            return true;
        }
        return ENUM_LIKE_CONSTANT_PATTERN.matcher(fieldValue).matches();
    }

    private static String renderCorrelationsYaml(List<SynthesizedCorrelationRule> rules) {
        if (rules.isEmpty()) {
            return "# No cross-field correlation rules needed\ncorrelations: []\n";
        }
        StringBuilder sb = new StringBuilder("# Auto-synthesized by `cats triage`\ncorrelations:\n");
        for (SynthesizedCorrelationRule r : rules) {
            sb.append("  - paths:\n");
            sb.append("      - \"").append(r.path()).append("\"\n");
            sb.append("    when: \"").append(r.whenField()).append(" == '").append(r.whenValue()).append("'\"\n");
            sb.append("    then:\n");
            if ("REMOVE".equals(r.action())) {
                sb.append("      omit:\n");
                sb.append("        - \"").append(r.targetField()).append("\"\n");
            } else {
                sb.append("      set:\n");
                sb.append("        ").append(r.targetField()).append(": \"<REQUIRED_VALUE>\"\n");
            }
        }
        return sb.toString();
    }

    private static String renderRefDataYaml(Map<String, Map<String, Object>> minedRefData) {
        if (minedRefData.isEmpty()) {
            return "# No 2XX path parameters or resource IDs mined from report\n";
        }
        StringBuilder sb = new StringBuilder("# Auto-mined from 2XX exchanges by `cats triage`\n");
        minedRefData.forEach((path, fields) -> {
            if (!fields.isEmpty()) {
                sb.append("\"").append(path).append("\":\n");
                fields.forEach((k, v) -> {
                    if (v instanceof Number || v instanceof Boolean) {
                        sb.append("  ").append(k).append(": ").append(v).append("\n");
                    } else {
                        sb.append("  ").append(k).append(": \"").append(String.valueOf(v).replace("\"", "\\\"")).append("\"\n");
                    }
                });
            }
        });
        return sb.toString();
    }

    private static String renderContractPatchYaml(Map<String, Map<String, Set<Integer>>> driftMap) {
        if (driftMap.isEmpty()) {
            return "# No undocumented status codes detected\npaths: {}\n";
        }
        StringBuilder sb = new StringBuilder("# OpenAPI contract patch generated by `cats triage`\npaths:\n");
        driftMap.forEach((path, byMethod) -> {
            sb.append("  ").append(path).append(":\n");
            byMethod.forEach((method, codes) -> {
                sb.append("    ").append(method).append(":\n");
                sb.append("      responses:\n");
                for (int code : codes) {
                    sb.append("        '").append(code).append("':\n");
                    sb.append("          description: \"").append(defaultStatusDescription(code)).append("\"\n");
                }
            });
        });
        return sb.toString();
    }

    private static String defaultStatusDescription(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            default -> "HTTP " + code;
        };
    }

    private static String buildCurlReproducer(CatsTestCase tc) {
        if (tc.getRequest() == null) {
            return "";
        }
        if (StringUtils.isBlank(tc.getRequest().getUrl()) && StringUtils.isNotBlank(tc.getFullRequestPath())) {
            tc.getRequest().setUrl(tc.getFullRequestPath());
        }
        return tc.getRequest().toCurlCommand();
    }

    private static String buildReproducersScript(TriageReport report) {
        StringBuilder sb = new StringBuilder("#!/usr/bin/env bash\nset -euo pipefail\n\n");
        sb.append("# Minimal Root-Cause Reproducers generated by `cats triage`\n");
        sb.append("# Clusters: ").append(report.totalClusters()).append(" (from ").append(report.totalErrors()).append(" failing tests)\n\n");
        int idx = 1;
        for (RootCauseCluster c : report.clusters()) {
            sb.append("# ").append(idx++).append(". [").append(c.category()).append("] ")
                    .append(c.operation()).append(" -> HTTP ").append(c.statusCode())
                    .append(" (").append(c.occurrences()).append(" tests: ").append(String.join(", ", c.sampleTestIds())).append(")\n");
            sb.append("# Diagnosis: ").append(c.diagnosis()).append("\n");
            sb.append(c.curlReproducer()).append("\n\n");
        }
        return sb.toString();
    }

    private void renderReport(TriageReport report) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(report));
            return;
        }
        logger.noFormat(AnsiUtils.bold("================================================================================"));
        logger.noFormat(AnsiUtils.bold("  CATS Intelligent Report Triage & Root-Cause Diagnosis"));
        logger.noFormat(AnsiUtils.bold("================================================================================"));
        logger.noFormat("  Report Folder      : " + report.reportFolder());
        logger.noFormat("  Tests Scanned      : " + report.totalTestsScanned()
                + "  (Errors: " + report.totalErrors() + ", Warnings: " + report.totalWarnings() + ")");
        logger.noFormat("  Root-Cause Clusters: " + report.totalClusters()
                + "  (Signal-to-Noise Reduction: " + report.noiseReductionPercent() + "%)");
        logger.noFormat("--------------------------------------------------------------------------------");

        int idx = 1;
        for (RootCauseCluster c : report.clusters()) {
            String badge = switch (c.severityRank()) {
                case 0 -> AnsiUtils.red("[" + c.category() + "]");
                case 1 -> AnsiUtils.yellow("[" + c.category() + "]");
                default -> AnsiUtils.blue("[" + c.category() + "]");
            };
            String verifyBadge = c.verificationStatus() == null ? ""
                    : ("FIXED".equals(c.verificationStatus())
                            ? " " + AnsiUtils.green("[VERIFIED FIXED -> HTTP " + c.verifiedStatusCode() + "]")
                            : " " + AnsiUtils.red("[STILL FAILING -> HTTP " + c.verifiedStatusCode() + "]"));
            logger.noFormat(String.format("  %2d. %s %s -> HTTP %d (%d tests)%s",
                    idx++, badge, AnsiUtils.bold(c.operation()), c.statusCode(), c.occurrences(), verifyBadge));
            logger.noFormat("     Diagnosis   : " + c.diagnosis());
            logger.noFormat("     Remediation : " + c.remediation());
            logger.noFormat("     Fuzzers     : " + String.join(", ", c.fuzzers()));
            logger.noFormat("     Sample Tests: " + String.join(", ", c.sampleTestIds()));
            logger.noFormat("     Reproducer  : " + c.curlReproducer());
            logger.noFormat("");
        }

        if (!report.synthesizedCorrelations().isEmpty()) {
            logger.noFormat(AnsiUtils.bold("  Synthesized --correlations YAML Rules (pass --export-correlations <file>):"));
            logger.noFormat("--------------------------------------------------------------------------------");
            for (String line : report.synthesizedCorrelationsYaml().split("\n")) {
                logger.noFormat("  " + line);
            }
        }
        logger.noFormat(AnsiUtils.bold("================================================================================"));
    }

    record IndexedTestCase(int number, CatsTestCase testCase) {
    }

    record ClusterClassification(String clusterKey,
                                 String category,
                                 int severityRank,
                                 String operation,
                                 int statusCode,
                                 String diagnosis,
                                 String remediation) {
    }

    private static final class ClusterAccumulator {
        private final ClusterClassification classification;
        private final String curlReproducer;
        private final CatsTestCase representativeTestCase;
        private int count;
        private final Set<String> fuzzers = new LinkedHashSet<>();
        private final List<String> sampleTestIds = new ArrayList<>();

        ClusterAccumulator(ClusterClassification classification, String curlReproducer, CatsTestCase representativeTestCase) {
            this.classification = classification;
            this.curlReproducer = curlReproducer;
            this.representativeTestCase = representativeTestCase;
        }

        void add(String testId, String fuzzer) {
            count++;
            if (StringUtils.isNotBlank(fuzzer) && fuzzers.size() < 6) {
                fuzzers.add(fuzzer);
            }
            if (StringUtils.isNotBlank(testId) && sampleTestIds.size() < 5) {
                sampleTestIds.add(testId);
            }
        }

        RootCauseCluster toCluster(OkHttpClient verifyClient, String serverOverride, Map<String, String> headerOverrides) {
            String verificationStatus = null;
            Integer verifiedCode = null;
            if (verifyClient != null && representativeTestCase != null && representativeTestCase.getRequest() != null) {
                int replayedCode = executeLiveVerification(verifyClient, representativeTestCase.getRequest(), serverOverride, headerOverrides);
                verifiedCode = replayedCode;
                verificationStatus = isVerifiedFixed(classification.category(), representativeTestCase.getExpectedResult(), replayedCode)
                        ? "FIXED"
                        : "STILL_FAILING";
            }
            return new RootCauseCluster(
                    classification.category(),
                    classification.severityRank(),
                    classification.operation(),
                    classification.statusCode(),
                    count,
                    classification.diagnosis(),
                    classification.remediation(),
                    List.copyOf(fuzzers),
                    List.copyOf(sampleTestIds),
                    curlReproducer,
                    verificationStatus,
                    verifiedCode);
        }

        private static boolean isVerifiedFixed(String category, String expectedResult, int replayedCode) {
            if ("SERVER_CRASH_5XX".equals(category)) {
                return replayedCode >= 200 && replayedCode < 500;
            }
            if (expectedResult != null && expectedResult.contains("4XX") && !expectedResult.contains("2XX")) {
                return ResponseCodeFamily.is4xxCode(replayedCode);
            }
            return ResponseCodeFamily.is2xxCode(replayedCode);
        }

        private static int executeLiveVerification(OkHttpClient client, CatsRequest catsRequest,
                                                   String serverOverride, Map<String, String> headerOverrides) {
            try {
                String targetUrl = DynamicValueResolver.resolve(resolveTargetUrl(catsRequest.getUrl(), serverOverride), Map.of());
                String method = StringUtils.defaultIfBlank(catsRequest.getHttpMethod(), "GET").toUpperCase(Locale.ROOT);
                Headers.Builder headersBuilder = new Headers.Builder();
                String contentType = "application/json";
                if (catsRequest.getHeaders() != null) {
                    for (KeyValuePair<String, Object> h : catsRequest.getHeaders()) {
                        if (StringUtils.isNotBlank(h.getKey()) && h.getValue() != null) {
                            String resolvedHeaderVal = DynamicValueResolver.resolve(String.valueOf(h.getValue()), Map.of());
                            headersBuilder.add(h.getKey(), resolvedHeaderVal);
                            if ("content-type".equalsIgnoreCase(h.getKey())) {
                                contentType = resolvedHeaderVal;
                            }
                        }
                    }
                }
                if (headerOverrides != null) {
                    headerOverrides.forEach(headersBuilder::set);
                }
                RequestBody body = null;
                if (HttpMethod.requiresBody(method) || catsRequest.isSendBody()) {
                    String payload = Objects.requireNonNullElse(catsRequest.getPayload(), "");
                    MediaType mediaType;
                    try {
                        mediaType = MediaType.parse(contentType);
                    } catch (RuntimeException _) {
                        mediaType = MediaType.parse("application/json");
                    }
                    body = RequestBody.create(payload.getBytes(StandardCharsets.UTF_8), mediaType);
                }
                Request request = new Request.Builder()
                        .url(targetUrl)
                        .headers(headersBuilder.build())
                        .method(method, body)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    return response.code();
                }
            } catch (IOException | RuntimeException _) {
                return 0;
            }
        }

        private static String resolveTargetUrl(String originalUrl, String serverOverride) {
            if (StringUtils.isBlank(serverOverride) || StringUtils.isBlank(originalUrl)) {
                return originalUrl;
            }
            HttpUrl orig = HttpUrl.parse(originalUrl);
            HttpUrl override = HttpUrl.parse(serverOverride);
            if (orig == null || override == null) {
                return originalUrl;
            }
            String overrideBasePath = override.encodedPath();
            String origPath = orig.encodedPath();
            String mergedPath = (!"/".equals(overrideBasePath) && !origPath.startsWith(overrideBasePath))
                    ? (overrideBasePath.endsWith("/") ? overrideBasePath.substring(0, overrideBasePath.length() - 1) : overrideBasePath) + origPath
                    : origPath;
            return orig.newBuilder()
                    .scheme(override.scheme())
                    .host(override.host())
                    .port(override.port())
                    .encodedPath(mergedPath)
                    .build()
                    .toString();
        }
    }

    public record RootCauseCluster(String category,
                                   int severityRank,
                                   String operation,
                                   int statusCode,
                                   int occurrences,
                                   String diagnosis,
                                   String remediation,
                                   List<String> fuzzers,
                                   List<String> sampleTestIds,
                                   String curlReproducer,
                                   String verificationStatus,
                                   Integer verifiedStatusCode) {
    }

    public record SynthesizedCorrelationRule(String path,
                                             String whenField,
                                             String whenValue,
                                             String action,
                                             String targetField,
                                             String sourceError) {
    }

    public record TriageReport(String reportFolder,
                               int totalTestsScanned,
                               int totalErrors,
                               int totalWarnings,
                               int totalClusters,
                               double noiseReductionPercent,
                               List<RootCauseCluster> clusters,
                               List<SynthesizedCorrelationRule> synthesizedCorrelations,
                               String synthesizedCorrelationsYaml,
                               String minedRefDataYaml,
                               String contractPatchYaml) {
    }
}
