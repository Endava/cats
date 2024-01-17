package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.fuzzer.special.TemplateFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.VersionProvider;
import com.google.common.net.HttpHeaders;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This will run the TemplateFuzzer based on a supplied template request payload, rather than OpenAPI specs.
 * The command expects a {@code --data} argument with the request payload for HTTP request with bodies or
 * a path containing query or path parameters for non-body requests.
 * The Fuzzers will only apply for fields supplied in the {@code --targetFields} argument.
 */
@CommandLine.Command(
        name = "fuzz",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Run Fuzzers based on a supplied request template or query & path params",
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
        footer = {"  Run fuzz tests for a given POST request:",
                "    cats fuzz -H header=value -X POST -d '{\"field1\":\"value1\",\"field2\":\"value2\",\"field3\":\"value3\"}' -t \"field1,field2,header\" -i \"2XX,4XX\" http://service-url ",
                "", "  Run fuzz tests for a given GET request:",
                "    cats fuzz -X GET -t \"path1,query1\" -i \"2XX,4XX\" http://service-url/paths1?query1=test&query2"},
        versionProvider = VersionProvider.class)
@Unremovable
public class TemplateFuzzCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TemplateFuzzCommand.class);
    @CommandLine.Parameters(index = "0",
            paramLabel = "<url>",
            description = "Full URL path of the service endpoint. This should include query params for non-body HTTP requests.")
    String url;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline API Options:|@%n", exclusive = false)
    ApiArguments apiArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArgs;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Match Options:|@%n", exclusive = false)
    MatchArguments matchArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Ignore Options:|@%n", exclusive = false)
    IgnoreArguments ignoreArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Dictionary Options:|@%n", exclusive = false)
    UserArguments userArguments;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    TemplateFuzzer templateFuzzer;

    @Inject
    TestCaseListener testCaseListener;

    @CommandLine.Option(names = {"--headers", "-H"},
            description = "Specifies the headers that will be passed along with the request and/or fuzzed. Default: @|bold,underline ${DEFAULT-VALUE}|@.")
    Map<String, String> headers = Map.of(HttpHeaders.ACCEPT, "application/json", HttpHeaders.CONTENT_TYPE, "application/json");

    @CommandLine.Option(names = {"--data", "-d"},
            description = "Specifies the request body used for fuzzing. The request body must be a valid request for the supplied url." +
                    "If the value of the argument starts with @|bold @|@ it will be considered a file.")
    String data;

    @CommandLine.Option(names = {"--httpMethod", "-X"},
            description = "The HTTP method. For HTTP method requiring a body you must also supply a  @|bold,underline --template|@. Default: @|bold,underline ${DEFAULT-VALUE}|@.")
    HttpMethod httpMethod = HttpMethod.POST;

    @CommandLine.Option(names = {"--targetFields", "-t"},
            description = "A comma separated list of fully qualified request fields, HTTP headers, path and query parameters that the Fuzzers will apply to." +
                    "For HTTP requests with bodies fuzzing will only run on request fields and/or HTTP headers. For HTTP request without bodies fuzzing will run on path and query parameters " +
                    "and/or HTTP headers.", split = ",", required = true)
    Set<String> targetFields;

    @Override
    public void run() {
        try {
            reportingArguments.processLogData();
            validateRequiredFields();
            String payload = this.loadPayload();
            logger.debug("Resolved payload: {}", payload);
            FuzzingData fuzzingData = FuzzingData.builder().path(url)
                    .contractPath(url)
                    .processedPayload(payload)
                    .method(httpMethod)
                    .headers(this.getHeaders())
                    .targetFields(targetFields)
                    .build();

            beforeFuzz();
            templateFuzzer.fuzz(fuzzingData);
            afterFuzz(fuzzingData.getContractPath(), fuzzingData.getMethod().name());
        } catch (IOException e) {
            logger.debug("Exception while fuzzing given data!", e);
            logger.error("Something went wrong while fuzzing. The data file does not exist or is not reachable: {}. Error message: {}", data, e.getMessage());
        }
    }

    private void afterFuzz(String path, String method) {
        testCaseListener.afterFuzz(path, method);
        testCaseListener.endSession();
    }

    private void beforeFuzz() throws IOException {
        testCaseListener.startSession();
        testCaseListener.initReportingPath();
        testCaseListener.beforeFuzz(templateFuzzer.getClass());
    }

    public Set<CatsHeader> getHeaders() {
        return headers.entrySet()
                .stream()
                .map(entry -> CatsHeader.builder()
                        .name(entry.getKey().trim())
                        .value(CatsDSLParser.parseAndGetResult(entry.getValue().trim(), authArgs.getAuthScriptAsMap()))
                        .build())
                .collect(Collectors.toSet());
    }

    private void validateRequiredFields() throws CommandLine.ParameterException {
        if (HttpMethod.requiresBody(httpMethod) && data == null) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Missing required option --data=<data>");
        }
    }

    private String loadPayload() throws IOException {
        if (data != null && data.startsWith("@")) {
            logger.debug("Resolving from file: {}", data);
            return Files.readString(Paths.get(data.substring(1)));
        } else if (data != null) {
            return data;
        }
        return "{}";
    }
}
