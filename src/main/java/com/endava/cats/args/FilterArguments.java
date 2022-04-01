package com.endava.cats.args;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.http.HttpMethod;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.springframework.core.annotation.AnnotationUtils;
import picocli.CommandLine;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@Getter
public class FilterArguments {
    static final List<String> FUZZERS_TO_BE_RUN = new ArrayList<>();
    static final List<Fuzzer> ALL_CATS_FUZZERS = new ArrayList<>();

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(FilterArguments.class);

    @Inject
    Instance<Fuzzer> fuzzers;
    @Inject
    IgnoreArguments ignoreArguments;
    @Inject
    CheckArguments checkArguments;
    @Inject
    ProcessingArguments processingArguments;

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


    public List<String> getSkipFuzzers() {
        return Optional.ofNullable(this.skipFuzzers).orElse(Collections.emptyList());
    }

    public List<String> getSkipPaths() {
        return Optional.ofNullable(this.skipPaths).orElse(Collections.emptyList());
    }

    public List<String> getSuppliedFuzzers() {
        return Optional.ofNullable(this.suppliedFuzzers).orElse(Collections.emptyList());
    }

    public List<String> getPaths() {
        return Optional.ofNullable(this.paths).orElse(Collections.emptyList());
    }

    public List<String> getFuzzersForPath() {
        if (FUZZERS_TO_BE_RUN.isEmpty()) {
            List<String> allowedFuzzers = processSuppliedFuzzers();
            allowedFuzzers = this.removeSkippedFuzzersGlobally(allowedFuzzers);
            allowedFuzzers = this.removeContractFuzzersIfNeeded(allowedFuzzers);
            allowedFuzzers = this.removeBasedOnTrimStrategy(allowedFuzzers);
            allowedFuzzers = this.removeBasedOnSanitizationStrategy(allowedFuzzers);

            FUZZERS_TO_BE_RUN.addAll(allowedFuzzers);
        }
        return FUZZERS_TO_BE_RUN;
    }

    public List<Fuzzer> getAllRegisteredFuzzers() {
        if (ALL_CATS_FUZZERS.isEmpty()) {
            List<String> interimFuzzersList = fuzzers.stream().map(Object::toString).collect(Collectors.toList());
            interimFuzzersList = this.removeBasedOnTrimStrategy(interimFuzzersList);

            List<String> finalFuzzersList = this.removeBasedOnSanitizationStrategy(interimFuzzersList);

            ALL_CATS_FUZZERS.addAll(fuzzers.stream().filter(fuzzer -> finalFuzzersList.contains(fuzzer.toString()))
                    .sorted(Comparator.comparing(fuzzer -> fuzzer.getClass().getSimpleName()))
                    .collect(Collectors.toList()));
        }

        return ALL_CATS_FUZZERS;
    }

    public List<String> removeBasedOnSanitizationStrategy(List<String> currentFuzzers) {
        Class<? extends Annotation> filterAnnotation = processingArguments.getSanitizationStrategy() == ProcessingArguments.SanitizationStrategy.SANITIZE_AND_VALIDATE
                ? ValidateAndSanitize.class : SanitizeAndValidate.class;
        List<String> trimFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, filterAnnotation);

        return currentFuzzers.stream().filter(fuzzer -> !trimFuzzers.contains(fuzzer))
                .collect(Collectors.toList());
    }

    public List<String> removeBasedOnTrimStrategy(List<String> currentFuzzers) {
        Class<? extends Annotation> filterAnnotation = processingArguments.getEdgeSpacesStrategy() == ProcessingArguments.TrimmingStrategy.TRIM_AND_VALIDATE
                ? ValidateAndTrim.class : TrimAndValidate.class;
        List<String> trimFuzzers = this.filterFuzzersByAnnotationWhenCheckArgumentSupplied(true, filterAnnotation);

        return currentFuzzers.stream().filter(fuzzer -> !trimFuzzers.contains(fuzzer))
                .collect(Collectors.toList());
    }

    private List<String> removeContractFuzzersIfNeeded(List<String> currentFuzzers) {
        if (ignoreArguments.isAnyIgnoredArgumentSupplied()) {
            return currentFuzzers.stream().filter(fuzzer -> !fuzzer.contains("Contract"))
                    .collect(Collectors.toList());
        }

        return currentFuzzers;
    }

    private List<String> processSuppliedFuzzers() {
        List<String> initialFuzzersList = this.constructFuzzersListFromCheckArguments();

        if (!this.getSuppliedFuzzers().isEmpty()) {
            List<String> suppliedFuzzerNames = suppliedFuzzers.stream().map(String::trim).collect(Collectors.toList());
            initialFuzzersList = initialFuzzersList.stream()
                    .filter(fuzzer -> suppliedFuzzerNames.stream().anyMatch(fuzzer::contains))
                    .collect(Collectors.toList());
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
        this.removeIfNotSupplied(checkArguments.isIncludeContract(), ContractInfoFuzzer.class, finalList);

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
            return fuzzers.stream().filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null)
                    .map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> removeSkippedFuzzersGlobally(List<String> allowedFuzzers) {
        List<String> fuzzersToExclude = this.getSkipFuzzers();

        return allowedFuzzers.stream()
                .filter(fuzzer ->
                        fuzzersToExclude.stream().noneMatch(fuzzer::contains))
                .collect(Collectors.toList());
    }

    public void customFilter(String specialFuzzer) {
        this.suppliedFuzzers = List.of(specialFuzzer);
        this.paths = Collections.emptyList();
        this.skipFuzzers = Collections.emptyList();
        this.skipPaths = Collections.emptyList();
        this.httpMethods = HttpMethod.restMethods();
        this.dryRun = false;
    }
}
