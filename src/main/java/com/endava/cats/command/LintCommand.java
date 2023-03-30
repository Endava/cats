package com.endava.cats.command;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.util.VersionProvider;
import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 * This is responsible to run all @ContractFuzzers
 */
@CommandLine.Command(
        name = "lint",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Run OpenAPI contract linters, also called ContractInfoFuzzers",
        abbreviateSynopsis = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Dependent
public class LintCommand implements Runnable {

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Naming Options:|@%n", exclusive = false)
    NamingArguments namingArguments;

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract")
    private String contract;

    @CommandLine.ParentCommand
    private CatsCommand catsCommand;


    @Override
    public void run() {
        catsCommand.apiArguments.setContract(contract);
        catsCommand.apiArguments.setServer("empty");
        catsCommand.filterArguments.customFilter("Contract");
        catsCommand.filterArguments.getCheckArguments().setIncludeContract(true);
        catsCommand.run();
    }
}
