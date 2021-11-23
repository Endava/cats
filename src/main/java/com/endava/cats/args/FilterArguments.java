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
import com.endava.cats.util.CatsUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    @Value("${dryRun:false}")
    private String dryRun;
    @Value("${ignoreResponseCodes:empty}")
    private String ignoreResponseCodes;
    @Value("${tests:empty}")
    private String tests;
    @Value("${ignoreResponseCodeUndocumentedCheck:empty}")
    private String ignoreResponseCodeUndocumentedCheck;
    @Value("${ignoreResponseBodyCheck:empty}")
    private String ignoreResponseBodyCheck;

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
    @Value("${arg.filter.dryRun.help:help}")
    private String dryRunHelp;
    @Value("${arg.filter.ignoreResponseCodes.help:help}")
    private String ignoreResponseCodesHelp;
    @Value("${arg.filter.tests.help:help}")
    private String testsHelp;
    @Value("${arg.filter.ignoreResponseCodeUndocumentedCheck.help:help}")
    private String ignoreResponseCodeUndocumentedCheckHelp;
    @Value("${arg.filter.ignoreResponseBodyCheck.help:help}")
    private String ignoreResponseBodyCheckHelp;

    @Autowired
    private List<Fuzzer> fuzzers;
    @Autowired
    private CheckArguments checkArguments;

    public void init() {
        args.add(CatsArg.builder().name("fuzzers").value(suppliedFuzzers).help(suppliedFuzzersHelp).build());
        args.add(CatsArg.builder().name("paths").value(paths).help(pathsHelp).build());
        args.add(CatsArg.builder().name("skipPaths").value(skipPaths).help(skipPathsHelp).build());
        args.add(CatsArg.builder().name("excludedFuzzers").value(skipFuzzers).help(skipFuzzersHelp).build());
        args.add(CatsArg.builder().name("skipXXXForPath").value(String.valueOf(skipFuzzersForPaths)).help(skipXXXForPathHelp).build());
        args.add(CatsArg.builder().name("skipFields").value(skipFields).help(skipFieldsHelp).build());
        args.add(CatsArg.builder().name("httpMethods").value(httpMethods).help(httpMethodsHelp).build());
        args.add(CatsArg.builder().name("dryRun").value(dryRun).help(dryRunHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseCodes").value(ignoreResponseCodes).help(ignoreResponseCodesHelp).build());
        args.add(CatsArg.builder().name("tests").value(tests).help(testsHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseCodeUndocumentedCheck").value(ignoreResponseCodeUndocumentedCheck).help(ignoreResponseCodeUndocumentedCheckHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseBodyCheck").value(ignoreResponseBodyCheck).help(ignoreResponseBodyCheckHelp).build());
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
        if (!CatsUtil.isArgumentValid(httpMethods)) {
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
        allowedFuzzers = this.removeContractFuzzersIfNeeded(allowedFuzzers);

        return allowedFuzzers;
    }

    private List<String> removeContractFuzzersIfNeeded(List<String> currentFuzzers) {
        if (!isEmptyIgnoredResponseCodes()) {
            return currentFuzzers.stream().filter(fuzzer -> !fuzzer.contains("Contract"))
                    .collect(Collectors.toList());
        }

        return currentFuzzers;
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
            finalList = fuzzers.stream().map(Object::toString).collect(Collectors.toList());
        }

        this.removeIfNotSupplied(checkArguments.includeControlChars(), ControlCharFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.includeEmojis(), EmojiFuzzer.class, finalList);
        this.removeIfNotSupplied(checkArguments.includeWhitespaces(), WhitespaceFuzzer.class, finalList);

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
        List<String> fuzzersToExclude = Stream.of(skipFuzzers.split(",")).map(String::trim).collect(Collectors.toList());

        return allowedFuzzers.stream()
                .filter(fuzzer ->
                        fuzzersToExclude.stream().noneMatch(fuzzer::contains))
                .collect(Collectors.toList());
    }

    public List<String> getIgnoreResponseCodes() {
        if (isEmptyIgnoredResponseCodes()) {
            return Collections.emptyList();
        }

        return Arrays.stream(ignoreResponseCodes.split(",")).map(code -> code.trim().strip())
                .collect(Collectors.toList());
    }

    public boolean isEmptyIgnoredResponseCodes() {
        return !CatsUtil.isArgumentValid(ignoreResponseCodes);
    }

    public boolean isIgnoredResponseCode(String receivedResponseCode) {
        return getIgnoreResponseCodes().stream()
                .anyMatch(code -> code.equalsIgnoreCase(receivedResponseCode)
                        || (code.substring(1, 3).equalsIgnoreCase("xx") && code.substring(0, 1).equalsIgnoreCase(receivedResponseCode.substring(0, 1))));

    }

    public boolean areTestCasesSupplied() {
        return CatsUtil.isArgumentValid(tests);
    }

    /**
     * Test Cases that do not have a json extension will be resolved from the cats-report folder.
     *
     * @return a list of all supplied test cases fully resolved
     */
    public List<String> parseTestCases() {
        return Arrays.stream(tests.split(","))
                .map(testCase -> testCase.trim().strip())
                .map(testCase -> testCase.endsWith(".json") ? testCase : "cats-report/" + testCase + ".json")
                .collect(Collectors.toList());

    }

    public boolean isIgnoreResponseCodeUndocumentedCheck() {
        return !"empty".equalsIgnoreCase(ignoreResponseCodeUndocumentedCheck);
    }

    public boolean isIgnoreResponseBodyCheck() {
        return !"empty".equalsIgnoreCase(ignoreResponseBodyCheck);
    }
}
