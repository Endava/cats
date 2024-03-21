package com.endava.cats.command;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.util.VersionProvider;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * This is responsible to run all @ContractFuzzers.
 */
@CommandLine.Command(
        name = "lint",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Run OpenAPI contract linters",
        abbreviateSynopsis = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command",
                "@|bold ERR|@:Where ERR is the number of errors reported by cats"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Lint an OpenAPI contract:",
                "    cats lint -c openapi.yml",
                "", "  Lint an OpenAPI contract and set expected naming convention for JSON objects to camelCase:",
                "    cats lint -c openapi.yml --jsonObjectsNaming CAMEL"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class LintCommand implements Runnable, CommandLine.IExitCodeGenerator {

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Naming Options:|@%n", exclusive = false)
    NamingArguments namingArguments;

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract")
    private String contract;

    @CommandLine.Option(names = {"--skipFuzzers"},
            description = "A comma separated list of fuzzers you want to ignore. You can use full or partial Fuzzer names", split = ",")
    private List<String> skipFuzzers;

    @CommandLine.ParentCommand
    private CatsCommand catsCommand;


    @Override
    public void run() {
        catsCommand.apiArguments.setContract(contract);
        catsCommand.apiArguments.setServer("http://empty");
        catsCommand.filterArguments.customFilter("Linter");
        catsCommand.filterArguments.getSkipFuzzers().addAll(Optional.ofNullable(skipFuzzers).orElse(Collections.emptyList()));
        catsCommand.filterArguments.getCheckArguments().setIncludeContract(true);
        catsCommand.run();
    }

    @Override
    public int getExitCode() {
        return catsCommand.getExitCode();
    }
}
