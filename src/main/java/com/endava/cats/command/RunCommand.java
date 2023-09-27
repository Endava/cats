package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import picocli.CommandLine;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
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
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Dependent
@Unremovable
public class RunCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RunCommand.class);

    @CommandLine.Parameters(index = "0",
            paramLabel = "<file>",
            description = "A yaml file following the CATS YAML syntax")
    File file;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline API Options:|@%n", exclusive = false)
    ApiArguments apiArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArgs;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--headers"},
            description = "Specifies custom headers that will be passed along with request. This can be used to pass oauth or JWT tokens for authentication purposed for example")
    private File headersFile;

    @CommandLine.Option(names = {"--queryParams"},
            description = "Specifies additional query parameters that will be passed along with request. This can be used to pass non-documented query params")
    private File queryFile;

    @CommandLine.Option(names = {"-H"},
            description = "Specifies the headers that will be passed along with the request. When supplied it will be applied to ALL paths. For per-path control use the `--headers` arg that requires a file.")
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
            description = "A custom mime type if the OpenAPI spec uses content type negotiation versioning. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private String contentType = "application/json";

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Ignore Options:|@%n", exclusive = false)
    IgnoreArguments ignoreArguments;

    @CommandLine.ParentCommand
    private CatsCommand catsCommand;

    @Override
    public void run() {
        apiArguments.validateRequired(spec);
        try {
            if (this.isFunctionalFuzzerFile()) {
                catsCommand.filterArguments.customFilter("FunctionalFuzzer");
                catsCommand.filesArguments.setCustomFuzzerFile(file);
            } else {
                catsCommand.filterArguments.customFilter("SecurityFuzzer");
                catsCommand.filesArguments.setSecurityFuzzerFile(file);
            }
            catsCommand.filesArguments.setHeadersFile(headersFile);
            catsCommand.filesArguments.setHeadersMap(headersMap);
            catsCommand.filesArguments.setCreateRefData(createRefData);
            catsCommand.filesArguments.setRefDataFile(refDataFile);
            catsCommand.filesArguments.setQueryFile(queryFile);
            catsCommand.processingArguments.setContentType(this.contentType);
            catsCommand.run();
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
