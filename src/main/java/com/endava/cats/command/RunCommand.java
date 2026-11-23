package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.command.model.CommandContext;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.fuzzer.FuzzerTypes;
import com.endava.cats.fuzzer.special.FunctionalFlowParser;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
                "", "  Run a sequential functional flow using the FunctionalFlowFuzzer:",
                "    cats run -c openapi.yml -s http://localhost:8080 functionalFlow.yml"},
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

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Files Options:|@%n", exclusive = false)
    FilesArguments filesArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Processing Options:|@%n", exclusive = false)
    ProcessingArguments processingArguments;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

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
            CommandContext context = createCommandContext();

            // Execute with context
            catsCommand.runWithContext(context);
        } catch (IOException e) {
            logger.debug("Exception while processing file!", e);
            logger.error("Something went wrong while processing input file: {}. The file might not exist or is not reachable. Error message: {}", file, e.getMessage());
        }
    }

    private @NonNull CommandContext createCommandContext() throws IOException {
        String content = Files.readString(file.toPath());
        if (!FunctionalFlowParser.isFunctionalFlowYaml(content)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Invalid flow file format in " + file + ". Legacy path-grouped YAML files are no longer supported; use the sequential 'steps:' FunctionalFlow format.");
        }
        CommandContext context = new CommandContext();
        if (FunctionalFlowParser.isSecurityFlowFile(file)) {
            context.setFuzzerType(FuzzerTypes.SECURITY);
            context.setSecurityFuzzerFile(file);
        } else {
            context.setFuzzerType(FuzzerTypes.FUNCTIONAL_FLOW);
            context.setFunctionalFlowFile(file);
        }
        context.setFilesArguments(filesArguments);
        return context;
    }

    @Override
    public int getExitCode() {
        return catsCommand.getExitCode();
    }
}
