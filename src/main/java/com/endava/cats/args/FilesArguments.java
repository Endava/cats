package com.endava.cats.args;

import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FieldCorrelationEvaluator;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument;
import com.endava.cats.fuzzer.special.FunctionalFlowParser;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Holds all arguments related to different files used by CATS like: headers, reference data, etc.
 */
@Singleton
public class FilesArguments {
    private static final String ALL = "all";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private Map<String, Map<String, Object>> headers;
    private Map<String, Map<String, Object>> queryParams;
    private Map<String, Map<String, Object>> refData;
    private List<String> urlParams;
    private List<String> pathsOrder;

    @Getter
    private Set<String> errorLeaksKeywordsList;
    @Getter
    private Properties fuzzConfigProperties;
    @Getter
    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();
    @Getter
    private Map<String, Map<String, Object>> securityFuzzerDetails = new HashMap<>();
    @Getter
    private FunctionalFlowDocument securityFlowDocument;


    @CommandLine.Option(names = {"--errorLeaksKeywords", "-K"},
            description = "A properties file with error leaks keywords that will be used when processing responses to detect potential error leaks. If one of these keyword is found, the test case will be marked as error")
    @Setter
    private File errorLeaksKeywords;

    @CommandLine.Option(names = {"--fuzzersConfig"},
            description = "A properties file with Fuzzer configuration that changes default Fuzzer's expected HTTP response codes. Configuration keys are prefixed with the Fuzzer name")
    @Setter
    private File fuzzersConfig;

    @CommandLine.Option(names = {"--urlParams"},
            description = "A comma separated list of @|bold name:value|@ pairs of parameters to be replaced inside the URLs", split = ",")
    @Setter
    private List<String> params;

    @CommandLine.Option(names = {"-P"},
            description = "Specifies the url/path params to be replaced in request paths")
    @Setter
    Map<String, Object> urlParamsArguments;

    @CommandLine.Option(names = {"--headers"},
            description = "A YAML file with custom headers that will be passed along with the request. Root elements are paths, while children are header_name:header_value pairs. This can be used to pass oauth or JWT tokens for authentication")
    @Setter
    private File headersFile;

    @CommandLine.Option(names = {"-H"},
            description = "Specifies the headers that will be passed along with the request. When supplied it will be applied to ALL paths. For per-path control, the `--headers` arg must be used")
    @Setter
    Map<String, Object> headersMap;

    @CommandLine.Option(names = {"--queryParams"},
            description = "A YAML file with additional query parameters that will be passed along with the request. This can be used to pass non-documented query params")
    @Setter
    private File queryFile;

    @CommandLine.Option(names = {"--pathsRunOrder"},
            description = "A file with the order in which the paths will be executed. The paths are on each line. The order from file will drive the execution order")
    @Setter
    private File pathsOrderFile;

    @CommandLine.Option(names = {"-Q"},
            description = "Specifies additional query parameters that will be passed along with the request. This can be used to pass non-documented query params. When supplied it will be applied to ALL paths. " +
                    "For per-path control, the `--queryParams` argument must be used")
    @Setter
    Map<String, Object> queryParamsArguments;

    @CommandLine.Option(names = {"--refData"},
            description = "A YAML file with fields that must have a fixed value in order for requests to succeed. " +
                    "If this is supplied when @|bold FunctionalFuzzer|@ is also enabled, the @|bold FunctionalFuzzer|@ will consider it a @|bold refData|@ template and try to replace any variables")
    @Setter
    @Getter
    private File refDataFile;

    @CommandLine.Option(names = {"-R"},
            description = "Specifies fields that must have a fixed value in order for requests to succeed. When supplied it will be applied to ALL paths. " +
                    "For per-path control, the `--refData` argument must be used")
    @Setter
    Map<String, Object> refDataArguments;


    @CommandLine.Option(names = {"--functionalFuzzerFile"},
            description = "The file used by the @|bold FunctionalFuzzer|@ to create user-supplied payloads and tests")
    @Getter
    @Setter
    private File customFuzzerFile;

    @Getter
    @Setter
    private File functionalFlowFile;

    @CommandLine.Option(names = {"--correlations", "--correlate"},
            description = "Specifies a YAML file with cross-field correlation rules (when/then set or omit) applied to request payloads")
    @Getter
    @Setter
    private File correlationsFile;

    @CommandLine.Option(names = {"--securityFuzzerFile"},
            description = "The file used by the @|bold SecurityFuzzer|@ to inject special strings in order to exploit possible vulnerabilities")
    @Setter
    private File securityFuzzerFile;

    @CommandLine.Option(names = {"--createRefData"},
            description = "This is only applicable when enabling the @|bold FunctionalFuzzer |@. It will instruct the @|bold FunctionalFuzzer|@ to create a @|bold,underline --refData|@ file " +
                    "with all the paths defined in the @|bold,underline customFuzzerFile|@ and the corresponding @|bold output|@ variables")
    @Getter
    @Setter
    private boolean createRefData;

    @CommandLine.Option(names = {"--mutators", "-m"},
            description = "A folder containing custom mutators. This argument is taken in consideration only when using the `cats random` sub-command")
    @Getter
    @Setter
    private File mutatorsFolder;

    /**
     * Copies configured file and parameter options from another {@link FilesArguments} instance.
     *
     * @param other the source {@link FilesArguments}
     */
    public void copyFrom(FilesArguments other) {
        if (other == null || other == this) {
            return;
        }
        this.errorLeaksKeywords = other.errorLeaksKeywords;
        this.fuzzersConfig = other.fuzzersConfig;
        this.params = other.params;
        this.urlParamsArguments = other.urlParamsArguments;
        this.headersFile = other.headersFile;
        this.headersMap = other.headersMap;
        this.queryFile = other.queryFile;
        this.pathsOrderFile = other.pathsOrderFile;
        this.queryParamsArguments = other.queryParamsArguments;
        this.refDataFile = other.refDataFile;
        this.refDataArguments = other.refDataArguments;
        this.customFuzzerFile = other.customFuzzerFile;
        this.functionalFlowFile = other.functionalFlowFile;
        this.correlationsFile = other.correlationsFile;
        this.securityFuzzerFile = other.securityFuzzerFile;
        this.securityFlowDocument = other.securityFlowDocument;
        this.createRefData = other.createRefData;
        this.mutatorsFolder = other.mutatorsFolder;
    }


    /**
     * Loads all supplied files for --securityFuzzerFile, --customFuzzerFile, --refData, --urlParams and --headers.
     *
     * @throws IOException if something happens during file processing
     */
    public void loadConfig() throws IOException {
        loadSecurityFuzzerFile();
        loadCustomFuzzerFile();
        loadRefData();
        loadURLParams();
        loadHeaders();
        loadQueryParams();
        loadFuzzConfigProperties();
        loadErrorLeaksKeywords();
        loadMutators();
        loadPathsOrder();
    }

    public void loadPathsOrder() throws IOException {
        if (pathsOrderFile == null) {
            log.debug("No paths order file provided");
            return;
        }
        pathsOrder = Files.readAllLines(pathsOrderFile.toPath());

        log.config(AnsiUtils.bold("Paths order file: {}"),
                AnsiUtils.blue(pathsOrderFile.getCanonicalPath()));
    }

    public void loadMutators() throws IOException {
        if (mutatorsFolder == null) {
            log.debug("No custom Mutators folder provided");
            return;
        }
        log.config(AnsiUtils.bold("Custom Mutators folder: {}"),
                AnsiUtils.blue(mutatorsFolder.getCanonicalPath()));
    }

    /**
     * Loads the supplied security fuzzer file into a Map.
     *
     * @throws IOException if something happens while reading the file
     */
    public void loadSecurityFuzzerFile() throws IOException {
        if (FunctionalFlowParser.isFunctionalFlowFile(securityFuzzerFile)) {
            log.config(AnsiUtils.bold("SecurityFuzzer file: {}"),
                    AnsiUtils.blue(securityFuzzerFile.getCanonicalPath()));
            securityFlowDocument = FunctionalFlowParser.parse(securityFuzzerFile);
            securityFuzzerDetails = new HashMap<>();
            return;
        }
        securityFlowDocument = null;
        securityFuzzerDetails = this.loadFileAsMapOfMapsOfStrings(securityFuzzerFile, "SecurityFuzzer");
    }

    /**
     * Loads the supplied functional fuzzer file into a Map.
     *
     * @throws IOException if something happens while reading the file
     */
    public void loadCustomFuzzerFile() throws IOException {
        customFuzzerDetails = this.loadFileAsMapOfMapsOfStrings(customFuzzerFile, "FunctionalFuzzer");
    }

    /**
     * Loads the supplied reference data file into a Map.
     *
     * @throws IOException if something happens while reading the file
     */
    public void loadRefData() throws IOException {
        this.refData = this.loadFileAsMapOfMapsOfStrings(refDataFile, "Reference Data");
        this.refData.merge(ALL, Optional.ofNullable(refDataArguments).orElse(Collections.emptyMap()), (existingValue, newValue) -> {
            existingValue.putAll(newValue);
            return existingValue;
        });
    }

    /**
     * Loads the supplied query params file into a Map.
     *
     * @throws IOException if something happens while reading the file
     */
    public void loadQueryParams() throws IOException {
        this.queryParams = this.loadFileAsMapOfMapsOfStrings(queryFile, "Query Params");
        this.queryParams.merge(ALL, Optional.ofNullable(queryParamsArguments).orElse(Collections.emptyMap()), (existingValue, newValue) -> {
            existingValue.putAll(newValue);
            return existingValue;
        });
    }

    /**
     * Loads the supplied url params into a Map.
     */
    public void loadURLParams() {
        urlParams = new ArrayList<>(Optional.ofNullable(this.params).orElse(new ArrayList<>()));
        urlParams.addAll(Optional.ofNullable(urlParamsArguments).orElse(Collections.emptyMap()).entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .toList());

        if (!urlParams.isEmpty()) {
            log.config(AnsiUtils.bold("URL parameters: {}"),
                    AnsiUtils.blue(urlParams));
        }
    }

    /**
     * Loads the supplied headers file into a Map. The method also merges the headers supplied
     * in the file with the ones supplied via the {@code -H} argument.
     *
     * @throws IOException if something happens while reading the file
     */
    public void loadHeaders() throws IOException {
        this.headers = this.loadFileAsMapOfMapsOfStrings(headersFile, "Headers");

        /*Merge headers from file with the ones supplied using the -H argument*/
        if (headersMap != null) {
            headers.merge(ALL, headersMap, (stringStringMap, stringStringMap2) -> {
                Map<String, Object> mergedMap = new HashMap<>(stringStringMap);
                mergedMap.putAll(stringStringMap2);
                return mergedMap;
            });
        }
    }

    /**
     * Returns a list of the --urlParams supplied. The list will contain strings in the {@code name:value} pairs.
     *
     * @return a name:value list of url params to be replaced when calling the service
     */
    public List<String> getUrlParamsList() {
        return Optional.ofNullable(this.urlParams).orElse(Collections.emptyList());
    }

    /**
     * Gets a specific value of the provided url param name provided through the {@code --urlParams} argument.
     * If the url parameter is not found, an empty string will be returned.
     *
     * @param urlParamName the name of the URL parameter
     * @return the value of the url parameter if provided or empty string otherwise
     */
    public String getUrlParam(String urlParamName) {
        return this.getUrlParamsList().stream()
                .filter(param -> getUrlParamName(param).equals(urlParamName))
                .map(FilesArguments::getUrlParamValue)
                .findFirst()
                .orElse("");
    }

    /**
     * Tests if a given parameter is not present in the --urlParams argument.
     *
     * @param parameter the parameter to be tested
     * @return true if the parameter is not present in the --urlParams argument, false otherwise
     */
    public boolean isNotUrlParam(String parameter) {
        return parameter != null && this.getUrlParamsList()
                .stream()
                .map(FilesArguments::getUrlParamName)
                .noneMatch(parameter::equals);
    }

    /**
     * Replaces the current URL parameters with the {@code --urlParams} arguments supplied.
     * The URL parameters are expected to be included in curly brackets.
     *
     * @param startingUrl the base url: {@code http://localhost:8080/{petId}}
     * @return the initial URL with the parameters replaced with --urlParams supplied values: {@code http://localhost:8080/123}
     */
    public String replacePathWithUrlParams(String startingUrl) {
        for (String nameValueParam : this.getUrlParamsList()) {
            String pathVar = "{" + getUrlParamName(nameValueParam) + "}";

            startingUrl = startingUrl.replace(pathVar, CatsUtil.urlEncodePathSegment(getUrlParamValue(nameValueParam)));
        }
        return startingUrl;
    }

    private static String getUrlParamName(String nameValueParam) {
        int separatorIndex = nameValueParam.indexOf(':');
        return separatorIndex < 0 ? nameValueParam : nameValueParam.substring(0, separatorIndex);
    }

    private static String getUrlParamValue(String nameValueParam) {
        int separatorIndex = nameValueParam.indexOf(':');
        return separatorIndex < 0 ? "" : nameValueParam.substring(separatorIndex + 1);
    }

    /**
     * Returns the header values supplied in the --headers argument.
     * <p>
     * The map keys are the contract paths or {@code all} if headers are applied to all paths.
     *
     * @param path the current path
     * @return a Map representation of the --headers file with paths being the Map keys
     */
    public Map<String, Object> getHeaders(String path) {
        return mergePathAndAll(headers, path, null);
    }

    public Map<String, Object> getHeaders(String path, HttpMethod method) {
        return mergePathAndAll(headers, path, method);
    }

    /**
     * Returns the reference data from the --refData file supplied as argument.
     * <p>
     * It returns the reference data for both the given path and the {@code all} key.
     *
     * @param currentPath the current API path
     * @return a Map with the supplied --refData
     */
    public Map<String, Object> getRefData(String currentPath) {
        return mergePathAndAll(refData, currentPath, null);
    }

    public Map<String, Object> getRefData(String currentPath, HttpMethod method) {
        return mergePathAndAll(refData, currentPath, method);
    }

    /**
     * Returns the list of field names marked with {@link com.endava.cats.io.ServiceCaller#CATS_REMOVE_FIELD}
     * in {@code --refData} for the given path.
     *
     * @param refDataForPath the reference data map for the current API path
     * @return list of field names to remove/skip from fuzzing
     */
    public static List<String> getFieldsToRemoveFromRefData(Map<String, Object> refDataForPath) {
        if (refDataForPath == null || refDataForPath.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> removed = new HashSet<>();
        refDataForPath.forEach((key, val) -> {
            if ("omit".equalsIgnoreCase(key)) {
                extractOmittedFields(val).forEach(f -> removed.add(FieldCorrelationEvaluator.normalizeFieldPath(f)));
            } else if (ServiceCaller.CATS_REMOVE_FIELD.equalsIgnoreCase(String.valueOf(val))) {
                removed.add(FieldCorrelationEvaluator.normalizeFieldPath(key));
            }
        });
        return List.copyOf(removed);
    }

    private static List<String> extractOmittedFields(Object omitValue) {
        if (omitValue instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(o -> String.valueOf(o).trim())
                    .filter(Predicate.not(String::isEmpty)).toList();
        }
        if (omitValue instanceof String str && !str.isBlank()) {
            List<String> items = new ArrayList<>();
            for (String token : str.replace("[", "").replace("]", "").split(",", -1)) {
                if (!token.isBlank()) {
                    items.add(token.trim());
                }
            }
            return items;
        }
        return List.of();
    }

    /**
     * Returns a map with key-value pairs for all additional query parameters corresponding
     * to the given path as well as the ALL entry.
     *
     * @param path the given path
     * @return a key-value map with all additional query params
     */
    public Map<String, Object> getAdditionalQueryParamsForPath(String path) {
        return mergePathAndAll(queryParams, path, null);
    }

    /**
     * Returns a list of paths as they are ordered in the pathsOrder file.
     *
     * @return a list of paths in the order they are defined in the pathsOrder file
     */
    public List<String> getPathsOrder() {
        return Optional.ofNullable(pathsOrder).orElse(Collections.emptyList());
    }

    /**
     * Loads the content of the file provided as argument in the {@code --fuzzersConfig} argument.
     *
     * @throws IOException if something goes wrong
     */
    public void loadFuzzConfigProperties() throws IOException {
        fuzzConfigProperties = new Properties();
        if (fuzzersConfig == null) {
            log.debug("No Fuzzer custom configuration provided!");
            return;
        }

        log.config(AnsiUtils.bold("Fuzzers custom configuration: {}"),
                AnsiUtils.blue(fuzzersConfig));
        try (InputStream stream = new FileInputStream(fuzzersConfig)) {
            fuzzConfigProperties.load(stream);
        }
    }

    /**
     * Loads the content of the file provided as argument in the {@code --errorLeaksKeywords} argument.
     *
     * @throws IOException if something goes wrong
     */
    public void loadErrorLeaksKeywords() throws IOException {
        errorLeaksKeywordsList = new HashSet<>();
        if (errorLeaksKeywords == null) {
            log.debug("No error leaks keywords file provided!");
            return;
        }

        errorLeaksKeywordsList = new HashSet<>(Files.readAllLines(errorLeaksKeywords.toPath()));

        log.config(AnsiUtils.bold("Error leaks keywords file: {}"),
                AnsiUtils.blue(errorLeaksKeywords.getCanonicalPath()));
    }

    Map<String, Object> mergePathAndAll(Map<String, Map<String, Object>> collection, String path, HttpMethod method) {
        if (collection == null || collection.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>();
        if (path != null) {
            if (method != null) {
                putMatchingEntries(collection, key -> isExactMethodPathMatch(key, path, method), result);
            }
            putMatchingEntries(collection, path::equals, result);
            if (method != null) {
                putMatchingEntries(collection, key -> isWildcardMethodPathMatch(key, path, method), result);
            }
            putMatchingEntries(collection, key -> isWildcardPathMatch(key, path), result);
        }
        putMatchingEntries(collection, ALL::equalsIgnoreCase, result);
        return result;
    }

    private static void putMatchingEntries(Map<String, Map<String, Object>> collection,
                                           Predicate<String> keyMatcher,
                                           Map<String, Object> target) {
        collection.forEach((k, v) -> {
            if (k != null && v != null && keyMatcher.test(k.trim())) {
                v.forEach((entryKey, entryVal) -> {
                    if ("omit".equalsIgnoreCase(entryKey)) {
                        extractOmittedFields(entryVal).forEach(f -> target.putIfAbsent(f, ServiceCaller.CATS_REMOVE_FIELD));
                    } else {
                        target.putIfAbsent(entryKey, entryVal);
                    }
                });
            }
        });
    }

    private static boolean isExactMethodPathMatch(String key, String path, HttpMethod method) {
        int space = key.indexOf(' ');
        if (space <= 0) {
            return false;
        }
        String keyMethod = key.substring(0, space).trim();
        String keyPath = key.substring(space + 1).trim();
        return method.name().equalsIgnoreCase(keyMethod) && path.equals(keyPath);
    }

    private static boolean isWildcardMethodPathMatch(String key, String path, HttpMethod method) {
        int space = key.indexOf(' ');
        if (space <= 0) {
            return false;
        }
        String keyMethod = key.substring(0, space).trim();
        String keyPath = key.substring(space + 1).trim();
        return method.name().equalsIgnoreCase(keyMethod) && isWildcardPathMatch(keyPath, path);
    }

    private static boolean isWildcardPathMatch(String pattern, String path) {
        if (!pattern.endsWith("*") || pattern.contains(" ")) {
            return false;
        }
        String prefix = pattern.substring(0, pattern.length() - 1);
        return !prefix.isEmpty() && path.startsWith(prefix);
    }

    private Map<String, Map<String, Object>> loadFileAsMapOfMapsOfStrings(File file, String fileType) throws IOException {
        try {
            if (file == null) {
                log.debug("No {} file provided!", fileType);
                return new HashMap<>();
            }

            log.config(AnsiUtils.bold("{} file: {}"), fileType,
                    AnsiUtils.blue(file.getCanonicalPath()));
            Map<String, Map<String, Object>> fromFile = parseYaml(file.getCanonicalPath());
            log.debug("{} file loaded successfully: {}", fileType, fromFile);

            return fromFile;
        } catch (IllegalArgumentException e) {
            throw new CatsException("File format is wrong for " + fileType + ". Make sure you supply a valid yaml file!", e);
        }
    }

    static Map<String, Map<String, Object>> parseYaml(String yaml) throws IOException {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (Reader reader = new InputStreamReader(new FileInputStream(yaml), StandardCharsets.UTF_8)) {
            JsonNode node = mapper.reader().readTree(reader);
            Map<String, Object> paths = mapper.convertValue(node, Map.class);

            for (Map.Entry<String, Object> entry : Optional.ofNullable(paths).orElse(Collections.emptyMap()).entrySet()) {
                Map<String, Object> properties = mapper.convertValue(entry.getValue(), LinkedHashMap.class);
                result.put(entry.getKey(), properties);
            }
        }
        return result;
    }
}
