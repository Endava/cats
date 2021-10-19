package com.endava.cats.args;

import com.endava.cats.fuzzer.*;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsSkipped;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.github.ludovicianul.prettylogger.config.level.ConfigFactory;
import io.github.ludovicianul.prettylogger.config.level.PrettyMarker;
import lombok.Getter;
import org.fusesource.jansi.Ansi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Getter
public class FilterArguments {
    private static final String ALL = "all";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(FilterArguments.class);
    private final List<CatsArg> args = new ArrayList<>();
    protected List<CatsSkipped> skipFuzzersForPaths;
    @Value("${fuzzers:all}")
    private String suppliedFuzzers;
    @Value("${paths:all}")
    private String paths;
    @Value("${skipPaths:empty}")
    private String skipPaths;
    @Value("${skipFuzzers:empty}")
    private String skipFuzzers;
    @Value("${skipFields:empty}")
    private String skipFields;
    @Value("${httpMethods:empty}")
    private String httpMethods;

    @Value("${arg.filter.fuzzers.help:help}")
    private String suppliedFuzzersHelp;
    @Value("${arg.filter.paths.help:help}")
    private String pathsHelp;
    @Value("${arg.filter.skipPaths.help:help}")
    private String skipPathsHelp;
    @Value("${arg.filter.skipFuzzers.help:help}")
    private String skipFuzzersHelp;
    @Value("${arg.filter.skipXXXForPath.help:help}")
    private String skipXXXForPathHelp;
    @Value("${arg.filter.skipFields.help:help}")
    private String skipFieldsHelp;
    @Value("${arg.filter.httpMethods.help:help}")
    private String httpMethodsHelp;

    @Autowired
    private List<Fuzzer> fuzzers;
    @Autowired
    private CheckArguments checkArguments;

    public void init() {
        args.add(CatsArg.builder().name("fuzzers").value(suppliedFuzzers).help(suppliedFuzzersHelp).build());
        args.add(CatsArg.builder().name("paths").value(paths).help(pathsHelp).build());
        args.add(CatsArg.builder().name("skipPaths").value(skipPaths).help(skipPathsHelp).build());
        args.add(CatsArg.builder().name("excludedFuzzers").value(skipFuzzers).help(skipFuzzersHelp).build());
        args.add(CatsArg.builder().name("skipXXXForPath").value(skipFuzzersForPaths.toString()).help(skipXXXForPathHelp).build());
        args.add(CatsArg.builder().name("skipFields").value(skipFields).help(skipFieldsHelp).build());
        args.add(CatsArg.builder().name("httpMethods").value(httpMethods).help(httpMethodsHelp).build());
    }

    /**
     * Triggers the processing of skipped Fuzzers for specific paths.
     *
     * @param args the program arguments
     */
    public void loadConfig(String... args) {
        this.processSkipFuzzerFor(args);
        init();
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

    public List<HttpMethod> getHttpMethods() {
        if ("empty".equals(httpMethods)) {
            return HttpMethod.restMethods();
        }

        return Arrays.stream(httpMethods.split(","))
                .map(String::trim).map(method -> HttpMethod.fromString(method).orElse(null))
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<String> getSkippedFields() {
        return Arrays.stream(skipFields.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private void processSkipFuzzerFor(String... args) {
        List<String> skipForArgs = Arrays.stream(args)
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

        return allowedFuzzers;
    }

    private List<String> processSuppliedFuzzers() {
        List<String> initialFuzzersList = this.constructFuzzersListFromCheckArguments();

        if (!ALL.equalsIgnoreCase(suppliedFuzzers)) {
            List<String> suppliedFuzzerNames = Stream.of(suppliedFuzzers.split(",")).map(String::trim).collect(Collectors.toList());
            initialFuzzersList = initialFuzzersList.stream()
                    .filter(fuzzer ->
                            suppliedFuzzerNames.stream().anyMatch(fuzzer::contains))
                    .collect(Collectors.toList());
        }

        return initialFuzzersList;
    }

    private List<String> constructFuzzersListFromCheckArguments() {
        List<String> finalList = new ArrayList<>();
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.checkFields(), FieldFuzzer.class));
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.checkContract(), ContractInfoFuzzer.class));
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.checkHeaders(), HeaderFuzzer.class));
        finalList.addAll(this.getFuzzersFromCheckArgument(checkArguments.checkHttp(), HttpFuzzer.class));

        if (finalList.isEmpty()) {
            return fuzzers.stream().map(Object::toString).collect(Collectors.toList());
        }
        return finalList;
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
        List<String> fuzzersToExclude = Stream.of(skipFuzzers.split(",")).map(String::trim).collect(Collectors.toList());

        return allowedFuzzers.stream()
                .filter(fuzzer ->
                        fuzzersToExclude.stream().noneMatch(fuzzer::contains))
                .collect(Collectors.toList());
    }
}
