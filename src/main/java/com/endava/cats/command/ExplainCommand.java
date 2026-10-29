package com.endava.cats.command;

import com.endava.cats.annotations.Linter;
import com.endava.cats.args.util.ProfileLoader;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.util.AnnotationUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

@CommandLine.Command(
        name = "explain",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {" Explain a 9XX response code:",
                "    cats explain --type response_code 953",
                "", " Get more information about an error reason:",
                "    cats explain --type error_reason \"Error details leak\"",
                "", " Explain a profile and its fuzzers:",
                "    cats explain --type profile security"},
        description = "Provides detailed information about a fuzzer, linter, profile, mutator, response code or error reason.",
        versionProvider = VersionProvider.class)
@Unremovable
public class ExplainCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Option(names = {"-t", "--type"},
            description = "The type of element to explain. Valid values: ${COMPLETION-CANDIDATES}", required = true)
    Type type;

    @CommandLine.Parameters(index = "0",
            paramLabel = "<info>",
            description = "The information you want to get details about.")
    String info;

    @Inject
    Instance<Fuzzer> fuzzers;

    @Inject
    Instance<Mutator> mutators;

    @Inject
    ProfileLoader profileLoader;


    @Override
    public void run() {
        CatsRandom.initRandom(0);
        switch (type) {
            case FUZZER -> displayFuzzerInfo();
            case LINTER -> displayLinterInfo();
            case PROFILE -> displayProfileInfo();
            case MUTATOR -> displayMutatorInfo();
            case RESPONSE_CODE -> displayResponseCodeInfo();
            case ERROR_REASON -> displayErrorReason();
        }
    }

    void displayErrorReason() {
        Arrays.stream(CatsResultFactory.Reason.values()).filter(reason -> reason.value()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)) || info.isBlank())
                .sorted()
                .forEach(reason -> logger.noFormat("* Reason {} - {}", reason.value(), reason.description()));
    }

    void displayFuzzerInfo() {
        fuzzers.stream().filter(fuzzer -> fuzzer.getClass().getSimpleName()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(fuzzer -> fuzzer.getClass().getSimpleName()))
                .forEach(fuzzer -> logger.noFormat("* Fuzzer {} - {}", fuzzer.getClass().getSimpleName(), fuzzer.description()));
    }

    void displayLinterInfo() {
        fuzzers.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), Linter.class) != null)
                .filter(fuzzer -> fuzzer.getClass().getSimpleName()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(fuzzer -> fuzzer.getClass().getSimpleName()))
                .forEach(fuzzer -> logger.noFormat("* Linter {} - {}", fuzzer.getClass().getSimpleName(), fuzzer.description()));
    }

    void displayProfileInfo() {
        var allProfiles = profileLoader.getAvailableProfilesDetails();
        boolean hasExactMatch = allProfiles.stream()
                .anyMatch(profile -> profile.name().equalsIgnoreCase(info));
        allProfiles.stream()
                .filter(profile -> hasExactMatch
                        ? profile.name().equalsIgnoreCase(info)
                        : profile.name().toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(ProfileLoader.Profile::name))
                .forEach(profile -> {
                    logger.noFormat("* Profile {} - {}", profile.name(), profile.description());
                    for (String fuzzerName : profile.fuzzers()) {
                        fuzzers.stream()
                                .filter(fuzzer -> ConsoleUtils.removeTrimSanitize(fuzzer.toString())
                                        .equalsIgnoreCase(ConsoleUtils.removeTrimSanitize(fuzzerName)))
                                .findFirst()
                                .ifPresentOrElse(
                                        fuzzer -> logger.noFormat("  - {} - {}", fuzzerName, fuzzer.description()),
                                        () -> logger.noFormat("  - {}", fuzzerName));
                    }
                });
    }

    void displayMutatorInfo() {
        mutators.stream().filter(mutator -> mutator.getClass().getSimpleName()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted()
                .forEach(mutator -> logger.noFormat("* Mutator {} - {}", mutator.getClass().getSimpleName(), mutator.description()));
    }

    void displayResponseCodeInfo() {
        Arrays.stream(CatsResponse.ExceptionalResponse.values())
                .map(CatsResponse.ExceptionalResponse::asString)
                .filter(response -> response.toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .toList().stream().sorted()
                .forEach(logger::noFormat);
    }

    public enum Type {
        FUZZER,
        LINTER,
        PROFILE,
        MUTATOR,
        RESPONSE_CODE,
        ERROR_REASON
    }
}
