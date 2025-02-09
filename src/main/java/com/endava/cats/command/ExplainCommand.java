package com.endava.cats.command;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Locale;

@CommandLine.Command(
        name = "explain",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Explain a 9XX response code:",
                "    cats explain --type response_code 953",
                "", "  Get more information about an error reason:",
                "    cats explain --type error_reason \"Error details leak\"",},
        description = "Provides detailed information about a fuzzer, mutator, response code or error reason.",
        versionProvider = VersionProvider.class)
@Unremovable
public class ExplainCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Option(names = {"-t", "--type"},
            description = "Output to console in JSON format.", required = true)
    private Type type;

    @CommandLine.Parameters(index = "0",
            paramLabel = "<info>",
            description = "The information you want to get details about.")
    String info;

    @Inject
    Instance<Fuzzer> fuzzers;

    @Inject
    Instance<Mutator> mutators;


    @Override
    public void run() {
        switch (type) {
            case FUZZER -> displayFuzzerInfo();
            case MUTATOR -> displayMutatorInfo();
            case RESPONSE_CODE -> displayResponseCodeInfo();
            case ERROR_REASON -> displayErrorReason();
        }
    }

    private void displayErrorReason() {
        Arrays.stream(CatsResultFactory.Reason.values()).filter(reason -> reason.name()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted()
                .forEach(reason -> logger.noFormat("* Reason {} - {}", reason.reason(), reason.description()));
    }

    private void displayFuzzerInfo() {
        fuzzers.stream().filter(fuzzer -> fuzzer.getClass().getSimpleName()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted()
                .forEach(fuzzer -> logger.noFormat("* Fuzzer {} - {}", fuzzer.getClass().getSimpleName(), fuzzer.description()));
    }

    private void displayMutatorInfo() {
        mutators.stream().filter(mutator -> mutator.getClass().getSimpleName()
                        .toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .sorted()
                .forEach(mutator -> logger.noFormat("* Mutator {} - {}", mutator.getClass().getSimpleName(), mutator.description()));
    }

    private void displayResponseCodeInfo() {
        Arrays.stream(CatsResponse.ExceptionalResponse.values())
                .map(CatsResponse.ExceptionalResponse::asString)
                .filter(response -> response.toLowerCase(Locale.ROOT).contains(info.toLowerCase(Locale.ROOT)))
                .toList().stream().sorted()
                .forEach(logger::noFormat);
    }

    public enum Type {
        FUZZER,
        MUTATOR,
        RESPONSE_CODE,
        ERROR_REASON
    }
}
