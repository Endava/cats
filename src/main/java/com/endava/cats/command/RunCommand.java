package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

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
        versionProvider = VersionProvider.class)
@Dependent
public class RunCommand implements Runnable {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(RunCommand.class);

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

    @CommandLine.Option(names = {"--createRefData"},
            description = "This is only applicable when enabling the @|bold CustomFuzzer |@. It will instruct the @|bold CustomFuzzer|@ to create a @|bold,underline --refData|@ file " +
                    "with all the paths defined in the @|bold,underline customFuzzerFile|@ and the corresponding @|bold output|@ variables")
    private boolean createRefData;

    @CommandLine.Option(names = {"--refData"},
            description = "Specifies the file with fields that must have a fixed value in order for requests to succeed. " +
                    "If this is supplied when @|bold CustomFuzzer|@ is also enabled, the @|bold CustomFuzzer|@ will consider it a @|bold refData|@ template and try to replace any variables")
    private File refDataFile;

    @CommandLine.Option(names = {"--contentType"},
            description = "A custom mime type if the OpenAPI spec uses content type negotiation versioning. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private String contentType = "application/json";

    @CommandLine.ParentCommand
    private CatsCommand catsCommand;

    @Override
    public void run() {
        apiArguments.validateRequired(spec);
        try {
            if (this.isCustomFuzzerFile()) {
                catsCommand.filterArguments.customFilter("CustomFuzzer");
                catsCommand.filesArguments.setCustomFuzzerFile(file);
            } else {
                catsCommand.filterArguments.customFilter("SecurityFuzzer");
                catsCommand.filesArguments.setSecurityFuzzerFile(file);
            }
            catsCommand.filesArguments.setHeadersFile(headersFile);
            catsCommand.filesArguments.setCreateRefData(createRefData);
            catsCommand.filesArguments.setRefDataFile(refDataFile);
            catsCommand.processingArguments.setContentType(this.contentType);
            catsCommand.run();
        } catch (IOException e) {
            LOGGER.error("Something went wrong while processing input file: {}", e.getMessage());
        }
    }

    private boolean isCustomFuzzerFile() throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());

        return lines.stream().noneMatch(line -> line.contains("targetFields"));
    }
}
