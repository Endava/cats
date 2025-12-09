package com.endava.cats.args;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.Linter;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.annotation.AnnotationUtils;
import picocli.CommandLine;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Arguments used to restrict CATS run footprint. The difference between Filter and Ignore arguments
 * is that Filter arguments are focused on input filtering while Ignore are focused on response filtering.
 */
@Singleton
public class FilterArguments {

    public enum TotalCountType {
        FUZZERS,
        LINTERS,
        ALL
    }

    /* local caches to avoid recompute */
    static final List<String> FUZZERS_TO_BE_RUN = new ArrayList<>();
    static final List<Fuzzer> SECOND_PHASE_FUZZERS_TO_BE_RUN = new ArrayList<>();
    static final List<Fuzzer> ALL_CATS_FUZZERS = new ArrayList<>();
    static final List<String> PATHS_TO_INCLUDE = new ArrayList<>();
    private static final String EXCLUDE_FROM_ALL_FUZZERS_MARK = "!";
    private boolean fuzzersToBeRunComputed = false;
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FilterArguments.class);

    @Inject
    Instance<Fuzzer> fuzzers;
    @Inject
    @Getter
    CheckArguments checkArguments;
    @Inject
    @Getter
    ProcessingArguments processingArguments;
    @Inject
    @Getter
    UserArguments userArguments;

    enum FieldType {
        STRING, NUMBER, INTEGER, BOOLEAN
    }

    enum FormatType {
        FLOAT, DOUBLE, INT32, INT64, DATE, DATE_TIME, PASSWORD, BYTE, BINARY, EMAIL, UUID, URI, URL, HOSTNAME, IPV4, IPV6
    }

    @CommandLine.Option(names = {"-f", "--fuzzers", "--fuzzer"},
            description = "A comma separated list of fuzzers to be run. They can be full or partial Fuzzer names. All available can be listed using: @|bold cats list -f|@", split = ",")
    private List<String> suppliedFuzzers;

    @CommandLine.Option(names = {"-p", "--paths", "--path"},
            description = "A comma separated list of paths to test. If no path is supplied, all paths will be considered. All available paths can be listed using: @|bold cats list -p -c api.yml|@", split = ",")
    @Setter
    private List<String> paths;

    @CommandLine.Option(names = {"--skipPaths", "--skipPath"},
            description = "A comma separated list of paths to ignore. If no path is supplied, no path will be ignored. All available paths can be listed using: @|bold cats list -p -c api.yml|@", split = ",")
    private List<String> skipPaths;

    @CommandLine.Option(names = {"--skipFuzzers", "--skipFuzzer"},
            description = "A comma separated list of fuzzers to ignore. They can be full or partial Fuzzer names. All available can be listed using: @|bold cats list -f|@", split = ",")
    @Setter
    private List<String> skipFuzzers;

    @CommandLine.Option(names = {"--httpMethods", "--httpMethod", "-X"},
            description = "A comma separated list of HTTP methods to include. Default: @|bold,underline ${DEFAULT-VALUE}|@", split = ",")
    @Setter
    private List<HttpMethod> httpMethods = HttpMethod.restMethods();

    @CommandLine.Option(names = {"--skipHttpMethods", "--skipHttpMethod"},
            description = "A comma separated list of HTTP methods to skip. Default: @|bold,underline ${DEFAULT-VALUE}|@", split = ",")
    @Setter
    private List<HttpMethod> skippedHttpMethods = Collections.emptyList();

    @CommandLine.Option(names = {"-d", "--dryRun"},
            description = "Simulate a possible run without actually invoking the service. This will print how many tests will actually be executed and with which Fuzzers")
    @Getter
    private boolean dryRun;

    @CommandLine.Option(names = {"--fieldTypes", "--fieldType"},
            description = "A comma separated list of OpenAPI data types to include. It only supports standard types: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FieldType> fieldTypes;

    @CommandLine.Option(names = {"--skipFieldTypes", "--skipFieldType"},
            description = "A comma separated list of OpenAPI data types to skip. It only supports standard types: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FieldType> skipFieldTypes;

    @CommandLine.Option(names = {"--fieldFormats", "--fieldFormat"},
            description = "A comma separated list of OpenAPI data formats to include. It supports formats mentioned in the documentation: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FormatType> fieldFormats;

    @CommandLine.Option(names = {"--skipFieldFormats", "--skipFieldFormat"},
            description = "A comma separated list of OpenAPI data formats to skip.  It supports formats mentioned in the documentation: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FormatType> skipFieldFormats;

    @CommandLine.Option(names = {"--skipFields", "--skipField"},
            description = "A comma separated list of fields that will be skipped by replacement Fuzzers like @|bold EmptyStringsInFields|@, @|bold NullValuesInFields|@, etc. " +
                    "If the field name starts with @|bold !|@ the field will be skipped by @|bold all|@ fuzzers. ", split = ",")
    private List<String> skipFields;

    @CommandLine.Option(names = {"--skipHeaders", "--skipHeader"},
            description = "A comma separated list of headers that will be skipped by all Fuzzers", split = ",")
    private List<String> skipHeaders;

    @CommandLine.Option(names = {"--skipDeprecatedOperations"},
            description = "Skip fuzzing deprecated API operations. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    @Getter
    private boolean skipDeprecated;

    @CommandLine.Option(names = {"-t", "--tags", "--tag"},
            description = "A comma separated list of tags to include. If no tag is supplied, all tags will be considered. All available tags can be listed using: @|bold cats stats -c api.yml|@", split = ",")
    private List<String> tags;

    @CommandLine.Option(names = {"--skipTags", "--skipTag"},
            description = "A comma separated list of tags to ignore. If no tag is supplied, no tag will be ignored. All available tags can be listed using: @|bold cats stats -c api.yml|@", split = ",")
    private List<String> skipTags;

    @CommandLine.Option(names = {"--skipFuzzersForExtension", "--skipFuzzerForExtension"},
            description = "Skip specific fuzzers for endpoints with certain OpenAPI extension values. " +
                    "Format: @|bold x-extension-name=value:Fuzzer1,Fuzzer2|@. " +
                    "Example: @|bold --skipFuzzersForExtension \"x-public-endpoint=true:BypassAuthentication\"|@ " +
                    "will skip BypassAuthentication fuzzer for all endpoints that have x-public-endpoint extension set to true.", split = ",")
    private List<String> skipFuzzersForExtension;

    @Setter
    private TotalCountType totalCountType = TotalCountType.FUZZERS;

    private Map<String, List<String>> skipPathFuzzers = new HashMap<>();
    private Map<String, Map<String, List<String>>> skipFuzzersForExtensionMap = new HashMap<>();

    /**
     * Gets the list of fields to skip during processing. If the list is not set, an empty list is returned.
     *
     * @return the list of fields to skip, or an empty list if not set
     */
    public List<String> getSkipFields() {
        return Optional.ofNullable(this.skipFields).orElse(Collections.emptyList());
    }

    /**
     * Return the fields that must be skipped by all fuzzers. Fields that must be skipped
     * by all fuzzers are marked with {@code !};
     *
     * @return a list of fields that can be skipped by all fuzzers
     */
    public List<String> getSkipFieldsToBeSkippedForAllFuzzers() {
        return this.getSkipFields().stream()
                .filter(field -> field.startsWith(EXCLUDE_FROM_ALL_FUZZERS_MARK))
                .map(field -> field.substring(1))
                .toList();
    }

    /**
     * Gets the list of headers to skip during processing. If the list is not set, an empty list is returned.
     *
     * @return the list of headers to skip, or an empty list if not set
     */
    public List<String> getSkipHeaders() {
        return Optional.ofNullable(this.skipHeaders).orElse(Collections.emptyList());
    }

    /**
     * Creates a list with the field data formats to be skipped based on the supplied {@code --skipFieldFormats} argument.
     *
     * @return the list of field data formats to skip if any supplied, an empty list otherwise
     */
    public List<String> getSkipFieldFormats() {
        return mapToString(Optional.ofNullable(this.skipFieldFormats).orElse(Collections.emptyList()));
    }

    /**
     * Creates a list with the field data formats to be included based on the supplied {@code --fieldFormats} argument.
     *
     * @return the list of field data formats to include if any supplied, an empty list otherwise
     */
    public List<String> getFieldFormats() {
        return mapToString(Optional.ofNullable(this.fieldFormats).orElse(Collections.emptyList()));
    }

    /**
     * Creates a list with the field data types to be skipped based on the supplied {@code --skipFieldTypes} argument.
     *
     * @return the list of field data types to skip if any supplied, an empty list otherwise
     */
    public List<String> getSkipFieldTypes() {
        return mapToString(Optional.ofNullable(this.skipFieldTypes).orElse(Collections.emptyList()));
    }

    /**
     * Creates a list with the field data types to be included based on the supplied {@code --fieldTypes} argument.
     *
     * @return the list of field type to include if any supplied, an empty list otherwise
     */
    public List<String> getFieldTypes() {
        return mapToString(Optional.ofNullable(this.fieldTypes).orElse(Collections.emptyList()));
    }

    /**
     * Creates a list with the fuzzers to be skipped based on the supplied {@code --skipFuzzers} argument.
     *
     * @return the list of fuzzers to skip if any supplied, an empty list otherwise
     */
    public List<String> getSkipFuzzers() {
        List<String> skipFuzzerListWithPathFuzzers = Optional.ofNullable(this.skipFuzzers).orElse(Collections.emptyList());
        skipPathFuzzers = skipFuzzerListWithPathFuzzers.stream()
                .filter(string -> string.contains("="))
                .map(String::trim)
                .map(string -> string.split("=", 2))
                .collect(Collectors.toMap(stringArray -> stringArray[0],
                        stringArray -> Stream.of(stringArray[1].split(","))
                                .map(String::trim)
                                .toList()));
        return skipFuzzerListWithPathFuzzers.stream()
                .filter(string -> !string.contains("="))
                .toList();
    }

    /**
     * Creates a list with the paths to be skipped based on the supplied {@code --skipPaths} argument.
     *
     * @return the list of paths to skip if any supplied, an empty list otherwise
     */
    public List<String> getSkipPaths() {
        return Optional.ofNullable(this.skipPaths).orElse(Collections.emptyList());
    }

    /**
     * Creates a list with the fuzzers to be included based on the supplied {@code --fuzzers} argument.
     * If no fuzzer is explicitly supplied, ALL fuzzers will be considered.
     *
     * @return the list of fuzzers to include if any supplied, an empty list otherwise
     */
    public List<String> getSuppliedFuzzers() {
        return Optional.ofNullable(this.suppliedFuzzers).orElse(Collections.emptyList());
    }

    /**
     * Creates a list of tags to be included based on the supplied {@code --tags} argument.
     * If no tag is supplied, all tags will be considered.
     *
     * @return the list of tags to include if any supplied, or an empty list otherwise
     */
    public List<String> getTags() {
        return Optional.ofNullable(this.tags).orElse(Collections.emptyList());
    }

    /**
     * Creates a list of tags to be skipped based on the supplied {@code --skipTags} argument.
     * If no tag is supplied, all tags will be considered.
     *
     * @return the list of tags to skip if any supplied, or an empty list otherwise
     */
    public List<String> getSkippedTags() {
        return Optional.ofNullable(this.skipTags).orElse(Collections.emptyList());
    }

    /**
     * Parses the {@code --skipFuzzersForExtension} argument and returns a map of extension name to
     * a map of extension value to list of fuzzers to skip.
     * Format: x-extension-name=value:Fuzzer1,Fuzzer2
     *
     * @return a map of extension name -> (extension value -> list of fuzzers to skip)
     */
    public Map<String, Map<String, List<String>>> getSkipFuzzersForExtension() {
        if (skipFuzzersForExtensionMap.isEmpty() && skipFuzzersForExtension != null) {
            for (String entry : skipFuzzersForExtension) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    String[] extensionParts = parts[0].split("=", 2);
                    if (extensionParts.length == 2) {
                        String extensionName = extensionParts[0].trim();
                        String extensionValue = extensionParts[1].trim();
                        List<String> fuzzersList = Stream.of(parts[1].split(","))
                                .map(String::trim)
                                .toList();
                        skipFuzzersForExtensionMap
                                .computeIfAbsent(extensionName, k -> new HashMap<>())
                                .put(extensionValue, fuzzersList);
                    }
                }
            }
        }
        return skipFuzzersForExtensionMap;
    }

    /**
     * Returns the list of fuzzers to skip for a given operation based on its extensions.
     *
     * @param operationExtensions the extensions map from the OpenAPI operation
     * @return a list of fuzzer names to skip for this operation
     */
    public List<String> getFuzzersToSkipForOperationExtensions(Map<String, Object> operationExtensions) {
        if (operationExtensions == null || operationExtensions.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> fuzzersToSkip = new ArrayList<>();
        Map<String, Map<String, List<String>>> extensionConfig = getSkipFuzzersForExtension();

        for (Map.Entry<String, Object> extension : operationExtensions.entrySet()) {
            String extensionName = extension.getKey();
            String extensionValue = String.valueOf(extension.getValue());

            Map<String, List<String>> valueToFuzzers = extensionConfig.get(extensionName);
            if (valueToFuzzers != null) {
                List<String> fuzzers = valueToFuzzers.get(extensionValue);
                if (fuzzers != null) {
                    fuzzersToSkip.addAll(fuzzers);
                }
            }
        }

        logger.debug("Fuzzers to skip for path based on extensions: {}", fuzzersToSkip);

        return fuzzersToSkip;
    }

    /**
     * Creates a list with the paths to be included based on the supplied {@code --paths} argument.
     * If no path is explicitly supplied, ALL fuzzers will be considered.
     *
     * @return the list of paths to include if any supplied, or an empty list otherwise
     */
    public List<String> getPaths() {
        return Optional.ofNullable(this.paths).orElse(Collections.emptyList());
    }

    /**
     * Returns the fuzzers to be run initially as a group as list of {@code Fuzzer}.
     *
     * @return a list of fuzzers to be run in phase 1
     */
    public List<Fuzzer> getFirstPhaseFuzzersAsFuzzers() {
        return this.getAllRegisteredFuzzers().stream()
                .filter(fuzzer -> this.getFirstPhaseFuzzersForPath().contains(fuzzer.toString()))
                .toList();
    }

    /**
     * Excludes fuzzers that are meant to be skipped for all the provided http methods list.
     *
     * @param httpMethods the list of http methods
     * @return a filtered list with fuzzers that can be run against at least one of the provided http method
     */
    public List<Fuzzer> filterOutFuzzersNotMatchingHttpMethodsAndPath(Set<HttpMethod> httpMethods, String path) {
        return this.getFirstPhaseFuzzersAsFuzzers().stream()
                .filter(fuzzer -> !new HashSet<>(fuzzer.skipForHttpMethods()).containsAll(httpMethods))
                .filter(fuzzer -> Optional.ofNullable(skipPathFuzzers.get(path))
                        .orElse(List.of())
                        .stream()
                        .noneMatch(string -> fuzzer.toString().contains(string)))
                .toList();
    }

    /**
     * Returns the fuzzers to be run initially as a list of fuzzer names.
     *
     * @return a list of fuzzers to be run in phase 1
     */
    public List<String> getFirstPhaseFuzzersForPath() {
        if (!fuzzersToBeRunComputed) {
            FUZZERS_TO_BE_RUN.clear();
            List<String> allowedFuzzers = processSuppliedFuzzers();
            allowedFuzzers = this.removeSkippedFuzzersGlobally(allowedFuzzers);
            allowedFuzzers = this.removeSpecialFuzzers(allowedFuzzers);
            allowedFuzzers = this.removeBasedOnTrimStrategy(allowedFuzzers);
            allowedFuzzers = this.removeBasedOnSanitizationStrategy(allowedFuzzers);

            FUZZERS_TO_BE_RUN.addAll(allowedFuzzers);
            fuzzersToBeRunComputed = true;
        }

        if (userArguments.isUserDictionarySupplied()) {
            FUZZERS_TO_BE_RUN.clear();
            // if a custom dictionary is supplied, we only keep these 2 fuzzers
            FUZZERS_TO_BE_RUN.addAll(List.of("UserDictionaryFieldsFuzzer", "UserDictionaryHeadersFuzzer"));
        } else {
            //second phase fuzzers are removed
            FUZZERS_TO_BE_RUN.removeIf(this.getSecondPhaseFuzzers().stream().map(Object::toString).toList()::contains);
        }
        return FUZZERS_TO_BE_RUN;
    }

    /**
     * Returns a list of fuzzers to be run as a group in phase 2.
     *
     * @return a list of fuzzers to be run in phase 2
     */
    public List<Fuzzer> getSecondPhaseFuzzers() {
        if (SECOND_PHASE_FUZZERS_TO_BE_RUN.isEmpty()) {
            List<String> secondPhaseFuzzersAsString = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, SecondPhaseFuzzer.class);
            List<String> fuzzersExcludingSkipped = secondPhaseFuzzersAsString.stream().filter(Predicate.not(this.getSkipFuzzers()::contains))
                    .filter(fuzzer -> this.getSuppliedFuzzers().isEmpty() || this.getSuppliedFuzzers().contains(fuzzer))
                    .toList();
            SECOND_PHASE_FUZZERS_TO_BE_RUN.addAll(this.getAllRegisteredFuzzers().stream()
                    .filter(fuzzer -> fuzzersExcludingSkipped.contains(fuzzer.toString()))
                    .toList());
        }
        if (containsOnlySpecialFuzzers(this.getSuppliedFuzzers())) {
            return Collections.emptyList();
        }
        return List.copyOf(SECOND_PHASE_FUZZERS_TO_BE_RUN);
    }

    private List<String> removeSpecialFuzzers(List<String> allowedFuzzers) {
        if (containsOnlySpecialFuzzers(allowedFuzzers)) {
            return allowedFuzzers;
        }
        List<String> specialFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, SpecialFuzzer.class);

        return allowedFuzzers.stream().filter(fuzzer -> !specialFuzzers.contains(fuzzer)).toList();
    }

    private boolean containsOnlySpecialFuzzers(List<String> candidates) {
        List<String> specialFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, SpecialFuzzer.class);

        return !candidates.isEmpty() && new HashSet<>(specialFuzzers).containsAll(candidates);
    }


    /**
     * Returns a list with ALL registered fuzzers.
     *
     * @return a list with all the fuzzers
     */
    public List<Fuzzer> getAllRegisteredFuzzers() {
        if (ALL_CATS_FUZZERS.isEmpty()) {
            List<String> interimFuzzersList = fuzzers.stream().map(Object::toString).toList();
            interimFuzzersList = this.removeBasedOnTrimStrategy(interimFuzzersList);

            List<String> finalFuzzersList = this.removeBasedOnSanitizationStrategy(interimFuzzersList);

            ALL_CATS_FUZZERS.addAll(fuzzers.stream().filter(fuzzer -> finalFuzzersList.contains(fuzzer.toString()))
                    .sorted(Comparator.comparing(fuzzer -> fuzzer.getClass().getSimpleName())).toList());
        }

        return ALL_CATS_FUZZERS;
    }

    List<String> removeBasedOnSanitizationStrategy(List<String> currentFuzzers) {
        Class<? extends Annotation> filterAnnotation = processingArguments.isSanitizeFirst() ? ValidateAndSanitize.class : SanitizeAndValidate.class;
        List<String> trimFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, filterAnnotation);

        return currentFuzzers.stream().filter(fuzzer -> !trimFuzzers.contains(fuzzer)).toList();
    }

    List<String> removeBasedOnTrimStrategy(List<String> currentFuzzers) {
        Class<? extends Annotation> filterAnnotation = processingArguments.getEdgeSpacesStrategy() == ProcessingArguments.TrimmingStrategy.TRIM_AND_VALIDATE
                ? ValidateAndTrim.class : TrimAndValidate.class;
        List<String> trimFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, filterAnnotation);

        return currentFuzzers.stream().filter(fuzzer -> !trimFuzzers.contains(fuzzer)).toList();
    }

    private List<String> processSuppliedFuzzers() {
        List<String> initialFuzzersList = this.constructFuzzersListFromCheckArguments();

        if (!this.getSuppliedFuzzers().isEmpty()) {
            List<String> suppliedFuzzerNames = suppliedFuzzers.stream().map(String::trim).toList();
            initialFuzzersList = initialFuzzersList.stream()
                    .filter(fuzzer -> suppliedFuzzerNames.stream().anyMatch(fuzzer::contains))
                    .toList();
        }

        return initialFuzzersList;
    }

    private List<String> constructFuzzersListFromCheckArguments() {
        List<String> finalList = new ArrayList<>();
        finalList.addAll(this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(checkArguments.isCheckFields(), FieldFuzzer.class));
        finalList.addAll(this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(checkArguments.isCheckHeaders(), HeaderFuzzer.class));
        finalList.addAll(this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(checkArguments.isCheckHttp(), HttpFuzzer.class));

        if (finalList.isEmpty()) {
            finalList = fuzzers.stream().map(Object::toString).toList();
        }

        finalList = this.filterByAnnotationAndIncludeArgument(checkArguments.isIncludeControlChars(), ControlCharFuzzer.class, finalList);
        finalList = this.filterByAnnotationAndIncludeArgument(checkArguments.isIncludeEmojis(), EmojiFuzzer.class, finalList);
        finalList = this.filterByAnnotationAndIncludeArgument(checkArguments.isIncludeWhitespaces(), WhitespaceFuzzer.class, finalList);
        finalList = this.filterByAnnotationAndIncludeArgument(checkArguments.isIncludeContract(), Linter.class, finalList);

        return finalList;
    }

    private List<String> filterByAnnotationAndIncludeArgument(boolean includeArgument, Class<? extends Annotation> annotation, List<String> currentFuzzers) {
        if (includeArgument) {
            return currentFuzzers;
        }

        return currentFuzzers.stream()
                .filter(fuzzerStr -> fuzzers.stream()
                        .noneMatch(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null
                                && fuzzer.toString().equals(fuzzerStr)))
                .toList();
    }

    private List<String> filterFuzzersByAnnotationWhenCheckArgumentSupplied(boolean checkArgument, Class<? extends Annotation> annotation) {
        if (checkArgument) {
            return fuzzers.stream()
                    .filter(fuzzer -> AnnotationUtils.isAnnotationDeclaredLocally(annotation, fuzzer.getClass()))
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    private List<String> removeSkippedFuzzersGlobally(List<String> allowedFuzzers) {
        List<String> fuzzersToExclude = this.getSkipFuzzers();

        return allowedFuzzers.stream()
                .filter(fuzzer ->
                        fuzzersToExclude.stream().noneMatch(fuzzer::contains))
                .toList();
    }

    /**
     * Configures only one fuzzer to be run.
     *
     * @param specialFuzzer the name of the special fuzzer to be run
     */
    public void customFilter(String specialFuzzer) {
        this.suppliedFuzzers = List.of(specialFuzzer);
        this.paths = Collections.emptyList();
        this.skipFuzzers = new ArrayList<>();
        this.skipPaths = Collections.emptyList();
        this.httpMethods = HttpMethod.restMethods();
        this.dryRun = false;
    }


    /**
     * Determines whether the current run includes the {@code Linter} fuzzer.
     * <p>
     * This is useful to distinguish whether a linting-specific analysis will be performed.
     *
     * @return {@code true} if the supplied fuzzers include "Linter", {@code false} otherwise
     */
    public boolean isLinting() {
        return this.getSuppliedFuzzers().stream()
                .anyMatch(fuzzer -> fuzzer.equalsIgnoreCase("Linter"));
    }

    /**
     * Convert list of enums to list of strings.
     *
     * @param fieldTypes types of fields
     * @param <E>        type of enum
     * @return a list of strings with enum names
     */
    public static <E extends Enum<E>> List<String> mapToString(List<E> fieldTypes) {
        return fieldTypes.stream().map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * Returns the paths to be run considering --paths and --skipPaths arguments.
     *
     * @param openAPI the OpenAPI spec
     * @return a list of paths to be run
     */
    public List<String> getPathsToRun(OpenAPI openAPI) {
        if (PATHS_TO_INCLUDE.isEmpty()) {
            PATHS_TO_INCLUDE.addAll(this.matchSuppliedPathsWithContractPaths(openAPI));
        }

        return PATHS_TO_INCLUDE;
    }

    /**
     * Check if there are any supplied paths and match them against the contract
     *
     * @param openAPI the OpenAPI object parsed from the contract
     * @return the list of paths from the contract matching the supplied list
     */
    public List<String> matchSuppliedPathsWithContractPaths(OpenAPI openAPI) {
        List<String> allSuppliedPaths = new ArrayList<>(matchWildCardPaths(this.getPaths(), openAPI));

        if (this.getPaths().isEmpty()) {
            allSuppliedPaths.addAll(openAPI.getPaths().keySet());
        }

        List<String> allSkippedPaths = matchWildCardPaths(this.getSkipPaths(), openAPI);
        allSuppliedPaths = allSuppliedPaths.stream().filter(path -> !allSkippedPaths.contains(path)).toList();

        logger.debug("Supplied paths before filtering {}", allSuppliedPaths);
        allSuppliedPaths = CatsUtil.filterAndPrintNotMatching(
                allSuppliedPaths,
                path -> openAPI.getPaths().containsKey(path),
                logger,
                "Supplied path is not matching the contract {}",
                Object::toString);
        logger.debug("Supplied paths after filtering {}", allSuppliedPaths);

        return allSuppliedPaths;
    }

    private List<String> matchWildCardPaths(List<String> paths, OpenAPI openAPI) {
        Set<String> allContractPaths = openAPI.getPaths().keySet();

        List<String> result = paths.stream()
                .flatMap(path -> {
                    if (!path.contains("*")) {
                        return Stream.of(path);
                    } else {
                        String regex = path
                                .replace("*", ".*")
                                .replace("{", "\\{")
                                .replace("}", "\\}");
                        Pattern pattern = Pattern.compile(regex);

                        return allContractPaths.stream()
                                .filter(contractPath -> pattern.matcher(contractPath).matches());
                    }
                })
                .distinct()
                .toList();

        logger.debug("Final list of matching wildcard paths: {}", result);
        return result;
    }

    /**
     * Checks if the supplied http method was supplied through arguments.
     *
     * @param method the HTTP method to check
     * @return true if the http method is supplied, false otherwise
     */
    public boolean isHttpMethodSupplied(HttpMethod method) {
        return this.getHttpMethods().contains(method);
    }

    /**
     * Returns the list of HTTP methods to be run excluding the ones that are skipped.
     *
     * @return a list of HTTP methods to be run
     */
    public List<HttpMethod> getHttpMethods() {
        return this.httpMethods
                .stream()
                .filter(httpMethod -> !skippedHttpMethods.contains(httpMethod))
                .toList();
    }

    /**
     * Returns the total number of fuzzers that will be run, excluding those that are annotated with
     * {@link TrimAndValidate}, {@link SanitizeAndValidate}, {@link SpecialFuzzer} and {@link Linter}.
     *
     * @return the total number of fuzzers to be run
     */
    public long getTotalFuzzers() {
        return fuzzers.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), TrimAndValidate.class) == null)
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), SanitizeAndValidate.class) == null)
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), SpecialFuzzer.class) == null)
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), Linter.class) == null)
                .count();
    }

    /**
     * Returns the total number of fuzzers that are annotated with {@link Linter}.
     *
     * @return the total number of linters to be run
     */
    public long getTotalLinters() {
        return fuzzers.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), Linter.class) != null)
                .count();
    }

    /**
     * Returns the total number of fuzzers or linters based on the {@link TotalCountType} set.
     *
     * @return the total number of fuzzers or linters
     */
    public long getTotalFuzzersOrLinters() {
        return switch (totalCountType) {
            case FUZZERS -> this.getTotalFuzzers();
            case LINTERS -> this.getTotalLinters();
            case ALL -> this.getTotalLinters() + this.getTotalFuzzers();
        };
    }
}
