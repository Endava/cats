package com.endava.cats.args;

import com.endava.cats.fuzzer.*;
import com.endava.cats.model.CatsSkipped;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    @Value("${excludedFuzzers:empty}")
    private String excludedFuzzers;

    @Value("${arg.filter.fuzzers.help:help}")
    private String suppliedFuzzersHelp;
    @Value("${arg.filter.paths.help:help}")
    private String pathsHelp;
    @Value("${arg.filter.skipPaths.help:help}")
    private String skipPathsHelp;
    @Value("${arg.filter.excludedFuzzers.help:help}")
    private String excludedFuzzersHelp;
    @Value("${arg.filter.skipXXXForPath.help:help}")
    private String skipXXXForPathHelp;

    @Autowired
    private List<Fuzzer> fuzzers;
    @Autowired
    private CheckArguments checkArguments;

    public void init() {
        args.add(CatsArg.builder().name("fuzzers").value(suppliedFuzzers).help(suppliedFuzzersHelp).build());
        args.add(CatsArg.builder().name("paths").value(paths).help(pathsHelp).build());
        args.add(CatsArg.builder().name("skipPaths").value(paths).help(skipPathsHelp).build());
        args.add(CatsArg.builder().name("excludedFuzzers").value(excludedFuzzers).help(excludedFuzzersHelp).build());
        args.add(CatsArg.builder().name("skipXXXForPath").value(skipFuzzersForPaths.toString()).help(skipXXXForPathHelp).build());
    }

    public void loadConfig(String... args) {
        this.processSkipFuzzerFor(args);
        init();
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
        List<String> allFuzzersName = this.constructFuzzersList();
        List<String> allowedFuzzers = allFuzzersName;

        if (!ALL.equalsIgnoreCase(suppliedFuzzers)) {
            allowedFuzzers = Stream.of(suppliedFuzzers.split(",")).collect(Collectors.toList());
        }

        allowedFuzzers = this.removeSkippedFuzzers(pathKey, allowedFuzzers);
        allowedFuzzers = this.removeExcludedFuzzers(allowedFuzzers);

        return CatsUtil.filterAndPrintNotMatching(allowedFuzzers, allFuzzersName::contains, LOGGER, "Supplied Fuzzer does not exist {}", Object::toString);
    }

    private List<String> constructFuzzersList() {
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

    private List<String> removeSkippedFuzzers(String pathKey, List<String> allowedFuzzers) {
        return allowedFuzzers.stream().filter(fuzzer -> skipFuzzersForPaths.stream()
                .noneMatch(catsSkipped -> catsSkipped.getFuzzer().equalsIgnoreCase(fuzzer) && catsSkipped.getForPaths().contains(pathKey)))
                .collect(Collectors.toList());
    }

    private List<String> removeExcludedFuzzers(List<String> allowedFuzzers) {
        List<String> fuzzersToExclude = Stream.of(excludedFuzzers.split(",")).collect(Collectors.toList());
        return allowedFuzzers.stream().filter(fuzzer -> !fuzzersToExclude.contains(fuzzer)).collect(Collectors.toList());
    }

}
