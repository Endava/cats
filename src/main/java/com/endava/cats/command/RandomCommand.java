package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.command.model.CommandContext;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.fuzzer.FuzzerTypes;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.VersionProvider;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;

/**
 * Runs continuous fuzzing based on random payloads, rather than pre-defined fuzzers.
 */
@CommandLine.Command(
        name = "random",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Run continuous fuzzing based on random mutators, rather than pre-defined fuzzers",
        abbreviateSynopsis = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        synopsisHeading = "%nUsage: ",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command",
                "@|bold ERR|@:Where ERR is the number of errors reported by cats"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Run continuous fuzzing for path /my-path for 100 seconds and match 500 http response codes:",
                "    cats random -H header=value -X POST -p /my-path -s http://localhost:8080 --mc 500 --stopAfterTimeInSec 100  ",
                "", "   Run continuous fuzzing for path /my-path and match 500 http response codes and stop after 10 errors:",
                "    cats random -H header=value -X POST -p /my-path -s http://localhost:8080 --mc 500 --stopAfterErrors 10"},
        versionProvider = VersionProvider.class)
@Unremovable
public class RandomCommand implements Runnable, CommandLine.IExitCodeGenerator {

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline API Options:|@%n", exclusive = false)
    ApiArguments apiArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Match Options:|@%n", exclusive = false, multiplicity = "1")
    MatchArguments matchArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Files Options:|@%n", exclusive = false)
    FilesArguments filesArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Stop Options:|@%n", exclusive = false, multiplicity = "1")
    StopArguments stopArguments;

    @CommandLine.Option(names = {"--httpMethod", "-X"}, required = true,
            description = "The HTTP method. For HTTP methods requiring a body a  @|bold,underline --template|@ must be supplied. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    HttpMethod httpMethod = HttpMethod.POST;

    @CommandLine.Option(names = {"--contentType"},
            description = "A custom mime type if the OpenAPI contract/spec uses content type negotiation versioning")
    private String contentType;
    @CommandLine.Option(names = {"--oneOfSelection", "--anyOfSelection"},
            description = "A @|bold name:value|@ list of discriminator names and values that can be used to filter request payloads when objects use oneOf or anyOf definitions" +
                    " which result in multiple payloads for a single endpoint and http method")
    Map<String, String> xxxOfSelections;

    @CommandLine.Option(names = {"--path", "-p"}, required = true,
            description = "An API path for continuous fuzzing")
    String path;

    @CommandLine.Option(names = {"--seed"},
            description = "The seed to be used for random number generation. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private long seed;

    @CommandLine.ParentCommand
    CatsCommand catsCommand;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        // these are all throwing a ParameterException in case a mandatory argument is not provided
        apiArguments.validateRequired(spec);

        CommandContext context = new CommandContext();
        context.setFuzzerType(FuzzerTypes.RANDOM);
        context.setFilesArguments(filesArguments);
        context.setContentType(contentType);
        context.setXxxOfSelections(xxxOfSelections);
        context.setSeed(seed);
        context.setPaths(List.of(path));
        context.setHttpMethods(List.of(httpMethod));

        catsCommand.runWithContext(context);
    }

    @Override
    public int getExitCode() {
        return catsCommand.getExitCode();
    }
}
