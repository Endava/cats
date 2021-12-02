package com.endava.cats.args;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.fuzzer.ControlCharFuzzer;
import com.endava.cats.fuzzer.EmojiFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.fuzzer.WhitespaceFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsSkipped;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.github.ludovicianul.prettylogger.config.level.ConfigFactory;
import io.github.ludovicianul.prettylogger.config.level.PrettyMarker;
import lombok.Getter;
import org.fusesource.jansi.Ansi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Getter
public class FilterArguments {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(FilterArguments.class);
    protected List<CatsSkipped> skipFuzzersForPaths = new ArrayList<>();

    @CommandLine.Option(names = {"--fuzzers"},
            description = "A comma separated list of fuzzers you want to run. You can use full or partial Fuzzer names. To list all available fuzzers run: `./cats.jar list -f`", split = ",")
    private List<String> suppliedFuzzers;

    @CommandLine.Option(names = {"--paths"},
            description = "A comma separated list of paths to test. If no path is supplied, all paths will be considered. To list all available paths run: `./cats.jar list -p -c api.yml`", split = ",")
    private List<String> paths;

    @CommandLine.Option(names = {"--skipPaths"},
            description = "A comma separated list of paths to ignore. If no path is supplied, no path will be ignored. To list all available paths run: `./cats.jar list -p -c api.yml`", split = ",")
    private List<String> skipPaths;

    @CommandLine.Option(names = {"--skipFuzzers"},
            description = "A comma separated list of fuzzers you want to ignore. You can use full or partial Fuzzer names. To list all available fuzzers run: `./cats.jar list -f`", split = ",")
    private List<String> skipFuzzers;

    @CommandLine.Option(names = {"--httpMethods"},
            description = "A comma separated list of HTTP methods. When supplied, only these methods will be considered for each contract path. Default: ${DEFAULT-VALUE}", split = ",")
    private List<HttpMethod> httpMethods = HttpMethod.restMethods();

    @CommandLine.Option(names = {"--dryRun"},
            description = "Simulate a possible run without actually invoking the service. This will print how many tests will actually be executed and with which Fuzzers")
    private boolean dryRun;

    @Autowired
    private List<Fuzzer> fuzzers;

    @Autowired
    private IgnoreArguments ignoreArguments;

    @Autowired
    private CheckArguments checkArguments;

    /**
     * Triggers the processing of skipped Fuzzers for specific paths.
     *
     * @param args the program arguments
     */
    public void loadConfig(String... args) {
        this.processSkipFuzzerFor(List.of(Optional.ofNullable(args).orElse(new String[]{})));
    }

    /**
     * Prints a warning if the final list of Fuzzers to be run matches all Fuzzer registered in CATS.
     * <p>
     * This warning is printing because it will take significant time to run all Fuzzers and a split strategy should be in place.
     *
     * @param finalList the final list with Fuzzers to be applied after parsing all filter arguments
     */
    public void printWarningIfNeeded(List<String> finalList) {
        if (finalList.size() == fuzzers.size()) {
            PrettyMarker config = ConfigFactory.warning().bold(true).underline(true).showLabel(true).showSymbol(true);
            String warning = Ansi.ansi().bold().fg(Ansi.Color.YELLOW).a("\t\t\t !!!! WARNING !!!!").reset().toString();
            String text = Ansi.ansi().bold().fg(Ansi.Color.YELLOW).a("You are running with all Fuzzers enabled. This will take a long time to run!").reset().toString();
            String check = Ansi.ansi().bold().fg(Ansi.Color.YELLOW).a("\t\t Please check the CATS README page for slicing strategies!").reset().toString();
            LOGGER.log(config, " ");
            LOGGER.log(config, warning);
            LOGGER.log(config, text);
            LOGGER.log(config, check);
            LOGGER.log(config, " ");
        }
    }

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

    private void processSkipFuzzerFor(List<String> args) {
        List<String> skipForArgs = args.stream()
                .filter(arg -> arg.startsWith("--skip") && arg.contains("ForPath")).collect(Collectors.toList());

        List<CatsSkipped> catsSkipped = skipForArgs.stream()
                .map(skipFor -> skipFor.replace("--skip", "")
                        .replace("ForPath", "").split("="))
                .map(skipForArr -> CatsSkipped.builder()
                        .fuzzer(skipForArr[0].trim())
                        .forPaths(Stream.of(skipForArr[1].trim().split(",")).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
        LOGGER.start("skipXXXForPath supplied arguments: {}. Matching with registered fuzzers...", catsSkipped);

        this.skipFuzzersForPaths = catsSkipped.stream()
                .filter(skipped -> fuzzers.stream().map(Object::toString).anyMatch(fuzzerName -> fuzzerName.equalsIgnoreCase(skipped.getFuzzer())))
                .collect(Collectors.toList());
        LOGGER.complete("skipXXXForPath list after matching with registered fuzzers: {}", this.skipFuzzersForPaths);
    }

    public List<String> getFuzzersForPath(String pathKey) {
        List<String> allowedFuzzers = processSuppliedFuzzers();
        allowedFuzzers = this.removeSkippedFuzzersForPath(pathKey, allowedFuzzers);
        allowedFuzzers = this.removeSkippedFuzzersGlobally(allowedFuzzers);
        allowedFuzzers = this.removeContractFuzzersIfNeeded(allowedFuzzers);

        return allowedFuzzers;
    }

    private List<String> removeContractFuzzersIfNeeded(List<String> currentFuzzers) {
        if (!ignoreArguments.getIgnoreResponseCodes().isEmpty() || ignoreArguments.isBlackbox()) {
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
                    .filter(fuzzer ->
                            suppliedFuzzerNames.stream().anyMatch(fuzzer::contains))
                    .collect(Collectors.toList());
        }

        return initialFuzzersList;
    }

    private List<String> constructFuzzersListFromCheckArguments() {
        List<String> finalList = new ArrayList<>();
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.isCheckFields(), FieldFuzzer.class));
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.isCheckContract(), ContractInfoFuzzer.class));
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.isCheckHeaders(), HeaderFuzzer.class));
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.isCheckHttp(), HttpFuzzer.class));

        if (finalList.isEmpty()) {
            finalList = fuzzers.stream().map(Object::toString).collect(Collectors.toList());
        }

        this.removeIfNotSupplied(checkArguments.isIncludeControlChars(), ControlCharFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.isIncludeEmojis(), EmojiFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.isIncludeWhitespaces(), WhitespaceFuzzer.class, finalList);

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

    private List<String> getFuzzersFromCheckArgument(boolean checkArgument, Class<? extends Annotation> annotation) {
        if (checkArgument) {
            return fuzzers.stream().filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null)
                    .map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> removeSkippedFuzzersForPath(String pathKey, List<String> allowedFuzzers) {
        return allowedFuzzers.stream().filter(fuzzer -> skipFuzzersForPaths.stream()
                        .noneMatch(catsSkipped -> catsSkipped.getFuzzer().equalsIgnoreCase(fuzzer) && catsSkipped.getForPaths().contains(pathKey)))
                .collect(Collectors.toList());
    }

    private List<String> removeSkippedFuzzersGlobally(List<String> allowedFuzzers) {
        List<String> fuzzersToExclude = this.getSkipFuzzers();

        return allowedFuzzers.stream()
                .filter(fuzzer ->
                        fuzzersToExclude.stream().noneMatch(fuzzer::contains))
                .collect(Collectors.toList());
    }
}
