package com.endava.cats.command;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.github.ludovicianul.prettylogger.config.level.PrettyLevel;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Generates payloads based on the OpenAPI contract and supplied arguments.
 */
@CommandLine.Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "Generates valid requests based on the given OpenAPI spec",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Generate payloads for a POST on /test from a given OpenAPI contract:",
                "    cats generate -c openapi.yml -X POST -p /test",
                "", "  Generate payloads for a POST on /test from a given OpenAPI contract and replace firstName with a predefined value:",
                "    cats generate -c openapi.yml -X POST -p /test -R firstName=John"},
        versionProvider = VersionProvider.class)
@Unremovable
public class GenerateCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract/spec", required = true)
    private String contract;

    @CommandLine.Option(names = {"--httpMethod", "-X"},
            description = "The HTTP method. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    HttpMethod httpMethod = HttpMethod.POST;

    @CommandLine.Option(names = {"--path", "-p"},
            description = "The path to generate the payload for", required = true)
    private String path;

    @CommandLine.Option(names = {"--limit", "-l"},
            description = "Max number of payloads to return. This might be useful when request body uses oneOf/anyOf combinations")
    private int limit;

    @CommandLine.Option(names = {"-D", "--debug"},
            description = "Sets CATS log level to ALL. Useful for diagnosis when raising bugs")
    private boolean debug;

    @CommandLine.Option(names = {"--pretty"},
            description = "Pretty print output")
    private boolean pretty;

    @CommandLine.Option(names = {"--contentType"},
            description = "A custom mime type if the OpenAPI contract/spec uses content type negotiation versioning. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private String contentType = "application/json";

    @CommandLine.Option(names = {"--selfReferenceDepth", "-L"},
            description = "Max depth for request objects having cyclic dependencies. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int selfReferenceDepth = 4;

    @CommandLine.Option(names = {"--useExampless"}, negatable = true,
            description = "When set to @|bold true|@, it will use request body examples, schema examples and primitive properties examples from the OpenAPI contract when available. " +
                    "This is equivalent of using @|bold,underline --useRequestBodyExamples|@ @|bold,underline --useSchemaExamples|@ @|bold,underline --usePropertyExamples|@ @|bold,underline --useResponseBodyExamples|@." +
                    "Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Boolean useExamples;

    FuzzingDataFactory fuzzingDataFactory;
    CatsGlobalContext globalContext;
    ProcessingArguments processingArguments;

    @Inject
    public GenerateCommand(FuzzingDataFactory fuzzingDataFactory, CatsGlobalContext globalContext, ProcessingArguments processingArguments) {
        this.fuzzingDataFactory = fuzzingDataFactory;
        this.globalContext = globalContext;
        this.processingArguments = processingArguments;
    }

    private int exitCodeDueToErrors = 0;

    @Override
    public void run() {
        try {
            processingArguments.setSelfReferenceDepth(this.selfReferenceDepth);
            processingArguments.setUseExamples(this.useExamples);

            if (!debug) {
                PrettyLogger.enableLevels(PrettyLevel.CONFIG, PrettyLevel.FATAL);
            } else {
                PrettyLogger.enableLevels(PrettyLevel.values());
            }

            OpenAPI openAPI = OpenApiUtils.readOpenApi(contract);
            this.checkOpenAPI(openAPI);
            this.globalContext.init(openAPI, List.of(contentType), new Properties(), null, Set.of());

            PathItem pathItem = openAPI.getPaths().entrySet()
                    .stream().filter(openApiPath -> openApiPath.getKey().equalsIgnoreCase(path))
                    .map(Map.Entry::getValue).findFirst().orElseThrow(() -> new IllegalArgumentException("Provided path does not exist!"));

            List<FuzzingData> fuzzingDataList = fuzzingDataFactory.fromPathItem(path, pathItem, openAPI);
            List<FuzzingData> filteredBasedOnHttpMethod = fuzzingDataList.stream()
                    .filter(data -> data.getMethod().equals(httpMethod))
                    .toList();

            printResult(filteredBasedOnHttpMethod.stream().map(FuzzingData::getPayload).toList());
        } catch (IOException | IllegalArgumentException e) {
            logger.fatal("Something went wrong while running CATS: {}", e.toString());
            logger.debug("Stacktrace: {}", e);
            exitCodeDueToErrors = 192;
        }
    }

    void printResult(List<String> filteredBasedOnHttpMethod) {
        List<String> toPrintList = filteredBasedOnHttpMethod;

        if (pretty) {
            toPrintList = filteredBasedOnHttpMethod.stream()
                    .limit(limit > 0 ? limit : filteredBasedOnHttpMethod.size())
                    .map(JsonParser::parseString)
                    .map(JsonUtils.GSON::toJson).toList();
        }

        Object toPrint = toPrintList.size() == 1 ? toPrintList.getFirst() : toPrintList;

        logger.noFormat(toPrint.toString());
    }

    private void checkOpenAPI(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new IllegalArgumentException("Provided OpenAPI specs are invalid!");
        }
    }

    @Override
    public int getExitCode() {
        return exitCodeDueToErrors;
    }
}
