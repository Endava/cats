package com.endava.cats.command;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.command.model.CommandContext;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.fuzzer.FuzzerTypes;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.VersionProvider;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * This is responsible to run all @Linters.
 */
@CommandLine.Command(
        name = "lint",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Run OpenAPI contract linters",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Lint an OpenAPI contract:",
                "    cats lint -c openapi.yml",
                "", "  Lint an OpenAPI contract and set expected naming convention for JSON objects to camelCase:",
                "    cats lint -c openapi.yml --jsonObjectsNaming CAMEL"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class LintCommand implements Runnable, CommandLine.IExitCodeGenerator {

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Naming Options:|@%n", exclusive = false)
    NamingArguments namingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Quality Gate Options:|@%n", exclusive = false)
    QualityGateArguments qualityGateArguments;

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract/spec")
    private String contract;

    @CommandLine.Option(names = {"--linters", "--linter"},
            description = "A comma separated list of linters to run. They can be full or partial Linter names. All available can be listed using: @|bold cats list -l|@", split = ",")
    private List<String> linters;

    @CommandLine.Option(names = {"--skipLinters", "--skipLinter"},
            description = "A comma separated list of linters to be ignored. They can be full or partial Linter names, or path-scoped expressions like @|bold /path=Linter1;Linter2|@ or @|bold /api/me/*=PathPluralsLinter|@", split = ",")
    private List<String> skipLinters;

    @CommandLine.Option(names = {"-p", "--paths", "--path"},
            description = "A comma separated list of paths to lint. Supports wildcards (e.g. @|bold /api/*|@). All available paths can be listed using: @|bold cats list -p -c api.yml|@", split = ",")
    private List<String> paths;

    @CommandLine.Option(names = {"--skipPaths", "--skipPath"},
            description = "A comma separated list of paths to ignore. Supports wildcards. All available paths can be listed using: @|bold cats list -p -c api.yml|@", split = ",")
    private List<String> skipPaths;

    @CommandLine.Option(names = {"-t", "--tags", "--tag"},
            description = "A comma separated list of OpenAPI tags to include", split = ",")
    private List<String> tags;

    @CommandLine.Option(names = {"--skipTags", "--skipTag"},
            description = "A comma separated list of OpenAPI tags to ignore", split = ",")
    private List<String> skipTags;

    @CommandLine.Option(names = {"--operationIds", "--operationId"},
            description = "A comma separated list of operationIds to include", split = ",")
    private List<String> operationIds;

    @CommandLine.Option(names = {"--skipOperationIds", "--skipOperationId"},
            description = "A comma separated list of operationIds to ignore", split = ",")
    private List<String> skipOperationIds;

    @CommandLine.Option(names = {"--httpMethods", "--httpMethod", "-X"},
            description = "A comma separated list of HTTP methods to include", split = ",")
    private List<HttpMethod> httpMethods;

    @CommandLine.Option(names = {"--skipHttpMethods", "--skipHttpMethod"},
            description = "A comma separated list of HTTP methods to skip", split = ",")
    private List<HttpMethod> skipHttpMethods;

    @CommandLine.Option(names = {"--skipDeprecatedOperations"},
            description = "Skip linting deprecated API operations")
    private Boolean skipDeprecated;

    @CommandLine.Option(names = {"--skipLintersForExtension", "--skipLinterForExtension"},
            description = "Skip specific linters for endpoints with certain OpenAPI extension values. Format: @|bold x-extension-name=value:Linter1,Linter2|@", split = ",(?=x-)")
    private List<String> skipLintersForExtension;

    @CommandLine.ParentCommand
    private CatsCommand catsCommand;


    @Override
    public void run() {
        CommandContext context = new CommandContext();
        context.setContract(contract);
        context.setServer("http://empty");
        context.setFuzzerType(FuzzerTypes.LINTER);
        if (linters != null && !linters.isEmpty()) {
            context.setSuppliedFuzzers(linters);
        }
        context.setSkipFuzzers(Optional.ofNullable(skipLinters).orElse(Collections.emptyList()));
        context.setPaths(paths);
        context.setSkipPaths(Optional.ofNullable(skipPaths).orElse(Collections.emptyList()));
        context.setTags(tags);
        context.setSkipTags(skipTags);
        context.setOperationIds(operationIds);
        context.setSkipOperationIds(skipOperationIds);
        context.setHttpMethods(httpMethods);
        context.setSkippedHttpMethods(skipHttpMethods);
        context.setSkipDeprecated(skipDeprecated);
        context.setSkipFuzzersForExtension(skipLintersForExtension);
        context.setIncludeContract(true);
        context.setLimitXxxOfCombinations(6);
        context.setTotalCountType(FilterArguments.TotalCountType.LINTERS);

        namingArguments.loadVerbMapFile();

        catsCommand.runWithContext(context);
    }

    @Override
    public int getExitCode() {
        return catsCommand.getExitCode();
    }
}
