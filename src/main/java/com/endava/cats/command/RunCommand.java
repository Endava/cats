package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.command.model.CommandContext;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.fuzzer.FuzzerTypes;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * This will run custom tests written using the CATS YAML format.
 */
@CommandLine.Command(
        name = "run",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Run functional tests written in CATS YAML format",
        abbreviateSynopsis = true,
        synopsisHeading = "%nUsage: ",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Run custom payloads using the SecurityFuzzer:",
                "    cats run -c openapi.yml -s http://localhost:8080 securityFuzzer.yml",
                "", "  Run a set of functional tests using the FunctionalFuzzer:",
                "    cats run -c openapi.yml -s http://localhost:8080 functionalTests.yml"},
        versionProvider = VersionProvider.class)
@Unremovable
public class RunCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RunCommand.class);

    @CommandLine.Parameters(index = "0",
            paramLabel = "<file>",
            description = "A yaml file following the CATS YAML syntax")
    File file;

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

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--headers"},
            description = "Specifies custom headers that will be passed along with the request. This can be used to pass oauth or JWT tokens for authentication")
    private File headersFile;

    @CommandLine.Option(names = {"--queryParams"},
            description = "Specifies additional query parameters that will be passed along with the request. This can be used to pass non-documented query params")
    private File queryFile;

    @CommandLine.Option(names = {"-H"},
            description = "Specifies headers that will be passed along with the request. When supplied it will be applied to ALL paths. For per-path control the `--headers` arg must be used")
    Map<String, Object> headersMap;

    @CommandLine.Option(names = {"--createRefData"},
            description = "This is only applicable when enabling the @|bold FunctionalFuzzer |@. It will instruct the @|bold FunctionalFuzzer|@ to create a @|bold,underline --refData|@ file " +
                    "with all the paths defined in the @|bold,underline customFuzzerFile|@ and the corresponding @|bold output|@ variables")
    private boolean createRefData;

    @CommandLine.Option(names = {"--refData"},
            description = "Specifies the file with fields that must have a fixed value in order for requests to succeed. " +
                    "If this is supplied when @|bold FunctionalFuzzer|@ is also enabled, the @|bold FunctionalFuzzer|@ will consider it a @|bold refData|@ template and try to replace any variables")
    private File refDataFile;

    @CommandLine.Option(names = {"--contentType"},
            description = "A custom mime type if the OpenAPI contract/spec uses content type negotiation versioning")
    private String contentType;
    @CommandLine.Option(names = {"--oneOfSelection", "--anyOfSelection"},
            description = "A @|bold name:value|@ list of discriminator names and values that can be use to filter request payloads when objects use oneOf or anyOf definitions" +
                    " which result in multiple payloads for a single endpoint and http method")
    Map<String, String> xxxOfSelections;

    @CommandLine.Option(names = {"--seed"},
            description = "The seed to be used for random number generation. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private long seed;


    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Ignore Options:|@%n", exclusive = false)
    IgnoreArguments ignoreArguments;

    @CommandLine.ParentCommand
    private CatsCommand catsCommand;

    @Override
    public void run() {
        apiArguments.validateRequired(spec);

        if (CatsUtil.isFileEmpty(file)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "You must provide a valid non-empty <file>");
        }

        try {
            CommandContext context = new CommandContext();

            // Set fuzzer type based on file content
            if (this.isFunctionalFuzzerFile()) {
                context.setFuzzerType(FuzzerTypes.FUNCTIONAL);
                context.setCustomFuzzerFile(file);
            } else {
                context.setFuzzerType(FuzzerTypes.SECURITY);
                context.setSecurityFuzzerFile(file);
            }

            // Set other context properties
            context.setHeadersFile(headersFile);
            context.setHeadersMap(headersMap);
            context.setCreateRefData(createRefData);
            context.setRefDataFile(refDataFile);
            context.setQueryFile(queryFile);
            context.setContentType(contentType);
            context.setXxxOfSelections(xxxOfSelections);
            context.setSeed(seed);

            // Execute with context
            catsCommand.runWithContext(context);
        } catch (IOException e) {
            logger.debug("Exception while processing file!", e);
            logger.error("Something went wrong while processing input file: {}. The file might not exist or is not reachable. Error message: {}", file, e.getMessage());
        }
    }

    private boolean isFunctionalFuzzerFile() throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());

        return lines.stream().noneMatch(line -> line.contains(CatsDSLWords.TARGET_FIELDS) || line.contains(CatsDSLWords.TARGET_FIELDS_TYPES));
    }

    @Override
    public int getExitCode() {
        return catsCommand.getExitCode();
    }
}
