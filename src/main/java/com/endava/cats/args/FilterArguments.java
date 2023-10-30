package com.endava.cats.args;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.springframework.core.annotation.AnnotationUtils;
import picocli.CommandLine;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Arguments used to restrict CATS run footprint. The difference between Filter and Ignore arguments
 * is that Filter arguments are focused on input filtering while Ignore are focused on response filtering.
 */
@Singleton
@Getter
public class FilterArguments {
    static final List<String> FUZZERS_TO_BE_RUN = new ArrayList<>();

    static final List<String> SECOND_PHASE_FUZZERS_TO_BE_RUN = new ArrayList<>();
    static final List<Fuzzer> ALL_CATS_FUZZERS = new ArrayList<>();
    @Inject
    Instance<Fuzzer> fuzzers;
    @Inject
    CheckArguments checkArguments;
    @Inject
    ProcessingArguments processingArguments;
    @Inject
    UserArguments userArguments;

    public enum FieldType {
        STRING, NUMBER, INTEGER, BOOLEAN;
    }

    public enum FormatType {
        FLOAT, DOUBLE, INT32, INT64, DATE, DATE_TIME, PASSWORD, BYTE, BINARY, EMAIL, UUID, URI, URL, HOSTNAME, IPV4, IPV6
    }

    @CommandLine.Option(names = {"-f", "--fuzzers"},
            description = "A comma separated list of fuzzers you want to run. You can use full or partial Fuzzer names. To list all available fuzzers run: @|bold cats list -f|@", split = ",")
    private List<String> suppliedFuzzers;
    @CommandLine.Option(names = {"-p", "--paths"},
            description = "A comma separated list of paths to test. If no path is supplied, all paths will be considered. To list all available paths run: @|bold cats list -p -c api.yml|@", split = ",")
    private List<String> paths;
    @CommandLine.Option(names = {"--skipPaths"},
            description = "A comma separated list of paths to ignore. If no path is supplied, no path will be ignored. To list all available paths run: @|bold cats list -p -c api.yml|@", split = ",")
    private List<String> skipPaths;
    @CommandLine.Option(names = {"--skipFuzzers"},
            description = "A comma separated list of fuzzers you want to ignore. You can use full or partial Fuzzer names. To list all available fuzzers run: @|bold cats list -f|@", split = ",")
    private List<String> skipFuzzers;
    @CommandLine.Option(names = {"--httpMethods"},
            description = "A comma separated list of HTTP methods. When supplied, only these methods will be considered for each contract path. Default: @|bold,underline ${DEFAULT-VALUE}|@", split = ",")
    private List<HttpMethod> httpMethods = HttpMethod.restMethods();
    @CommandLine.Option(names = {"-d", "--dryRun"},
            description = "Simulate a possible run without actually invoking the service. This will print how many tests will actually be executed and with which Fuzzers")
    private boolean dryRun;
    @CommandLine.Option(names = {"--fieldTypes"},
            description = "A comma separated list of OpenAPI data types to include. It only supports standard types: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FieldType> fieldTypes;
    @CommandLine.Option(names = {"--skipFieldTypes"},
            description = "A comma separated list of OpenAPI data types to skip. It only supports standard types: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FieldType> skipFieldTypes;
    @CommandLine.Option(names = {"--fieldFormats"},
            description = "A comma separated list of OpenAPI data formats to include. It supports formats mentioned in the documentation: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FormatType> fieldFormats;
    @CommandLine.Option(names = {"--skipFieldFormats"},
            description = "A comma separated list of OpenAPI data formats to skip.  It supports formats mentioned in the documentation: @|underline https://swagger.io/docs/specification/data-models/data-types|@", split = ",")
    private List<FormatType> skipFieldFormats;
    @CommandLine.Option(names = {"--skipFields"},
            description = "A comma separated list of fields that will be skipped by replacement Fuzzers like @|bold EmptyStringsInFields|@, @|bold NullValuesInFields|@, etc.", split = ",")
    private List<String> skipFields;

    @CommandLine.Option(names = {"--skipHeaders"},
            description = "A comma separated list of headers that will be skipped by all Fuzzers", split = ",")
    private List<String> skipHeaders;


    public List<String> getSkipFields() {
        return Optional.ofNullable(this.skipFields).orElse(Collections.emptyList());
    }

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
        return Optional.ofNullable(this.skipFuzzers).orElse(Collections.emptyList());
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
     * Creates a list with the paths to be included based on the supplied {@code --paths} argument.
     * If no path is explicitly supplied, ALL fuzzers will be considered.
     *
     * @return the list of paths to include if any supplied, an empty list otherwise
     */
    public List<String> getPaths() {
        return Optional.ofNullable(this.paths).orElse(Collections.emptyList());
    }

    public List<String> getFirstPhaseFuzzersForPath() {
        if (FUZZERS_TO_BE_RUN.isEmpty()) {
            List<String> allowedFuzzers = processSuppliedFuzzers();
            allowedFuzzers = this.removeSkippedFuzzersGlobally(allowedFuzzers);
            allowedFuzzers = this.removeSpecialFuzzers(allowedFuzzers);
            allowedFuzzers = this.removeBasedOnTrimStrategy(allowedFuzzers);
            allowedFuzzers = this.removeBasedOnSanitizationStrategy(allowedFuzzers);

            FUZZERS_TO_BE_RUN.addAll(allowedFuzzers);
        }
        if (userArguments.getWords() != null) {
            FUZZERS_TO_BE_RUN.clear();
            FUZZERS_TO_BE_RUN.addAll(List.of("UserDictionaryFieldsFuzzer", "UserDictionaryHeadersFuzzer"));
        } else {
            FUZZERS_TO_BE_RUN.removeIf(this.getSecondPhaseFuzzers()::contains);
        }
        return FUZZERS_TO_BE_RUN;
    }

    public List<String> getSecondPhaseFuzzers() {
        if (SECOND_PHASE_FUZZERS_TO_BE_RUN.isEmpty()) {
            SECOND_PHASE_FUZZERS_TO_BE_RUN.addAll(this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, SecondPhaseFuzzer.class));
        }
        if (onlySpecialFuzzers(this.getSuppliedFuzzers())) {
            return Collections.emptyList();
        }
        return List.copyOf(SECOND_PHASE_FUZZERS_TO_BE_RUN);
    }

    private List<String> removeSpecialFuzzers(List<String> allowedFuzzers) {
        if (onlySpecialFuzzers(allowedFuzzers)) {
            return allowedFuzzers;
        }
        List<String> trimFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, SpecialFuzzer.class);

        return allowedFuzzers.stream().filter(fuzzer -> !trimFuzzers.contains(fuzzer)).toList();
    }

    private boolean onlySpecialFuzzers(List<String> candidates) {
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
        Class<? extends Annotation> filterAnnotation = processingArguments.getSanitizationStrategy() == ProcessingArguments.SanitizationStrategy.SANITIZE_AND_VALIDATE
                ? ValidateAndSanitize.class : SanitizeAndValidate.class;
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
            finalList = fuzzers.stream().map(Object::toString).collect(Collectors.toList());
        }

        this.removeIfNotSupplied(checkArguments.isIncludeControlChars(), ControlCharFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.isIncludeEmojis(), EmojiFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.isIncludeWhitespaces(), WhitespaceFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.isIncludeContract(), LinterFuzzer.class, finalList);

        return finalList;
    }

    private void removeIfNotSupplied(boolean includeArgument, Class<? extends Annotation> annotation, List<String> currentFuzzers) {
        if (!includeArgument) {
            for (Fuzzer fuzzer : fuzzers) {
                if (AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null) {
                    currentFuzzers.remove(fuzzer.toString());
                }
            }
        }
    }

    private List<String> filterFuzzersByAnnotationWhenCheckArgumentSupplied(boolean checkArgument, Class<? extends Annotation> annotation) {
        if (checkArgument) {
            return fuzzers.stream().filter(fuzzer -> AnnotationUtils.isAnnotationDeclaredLocally(annotation, fuzzer.getClass()))
                    .map(Object::toString).toList();
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

    public void customFilter(String specialFuzzer) {
        this.suppliedFuzzers = List.of(specialFuzzer);
        this.paths = Collections.emptyList();
        this.skipFuzzers = new ArrayList<>();
        this.skipPaths = Collections.emptyList();
        this.httpMethods = HttpMethod.restMethods();
        this.dryRun = false;
    }

    public static <E extends Enum<E>> List<String> mapToString(List<E> fieldTypes) {
        return fieldTypes.stream().map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
    }
}
