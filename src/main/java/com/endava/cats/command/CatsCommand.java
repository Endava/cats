package com.endava.cats.command;

import com.endava.cats.Fuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.factory.NoMediaType;
import com.endava.cats.fuzzer.fields.FunctionalFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import picocli.AutoComplete;
import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

@CommandLine.Command(
        name = "cats",
        mixinStandardHelpOptions = true,
        header = "%n@|green cats - OpenAPI fuzzer and negative testing tool; version ${app.version}|@ %n",
        usageHelpAutoWidth = true,
        versionProvider = VersionProvider.class,
        commandListHeading = "%n@|bold,underline Commands:|@%n",
        defaultValueProvider = CommandLine.PropertiesDefaultProvider.class,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        resourceBundle = "version",
        subcommands = {
                AutoComplete.GenerateCompletion.class,
                CommandLine.HelpCommand.class,
                ListCommand.class,
                ReplayCommand.class,
                RunCommand.class,
                TemplateFuzzCommand.class,
                LintCommand.class
        })
@Dependent
public class CatsCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CatsCommand.class);

    @Inject
    FuzzingDataFactory fuzzingDataFactory;
    @Inject
    FunctionalFuzzer functionalFuzzer;
    @Inject
    TestCaseListener testCaseListener;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline API Options:|@%n", exclusive = false)
    ApiArguments apiArguments;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArgs;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Check Options:|@%n", exclusive = false)
    CheckArguments checkArgs;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Files Options:|@%n", exclusive = false)
    FilesArguments filesArguments;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Filter Options:|@%n", exclusive = false)
    FilterArguments filterArguments;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Ignore Options:|@%n", exclusive = false)
    IgnoreArguments ignoreArguments;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Processing Options:|@%n", exclusive = false)
    ProcessingArguments processingArguments;
    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Dictionary Options:|@%n", exclusive = false)
    UserArguments userArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Match Options (they are only active when supplying a custom dictionary):|@%n", exclusive = false)
    MatchArguments matchArguments;
    
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    ExecutionStatisticsListener executionStatisticsListener;

    @Inject
    CatsGlobalContext globalContext;

    private int exitCodeDueToErrors;

    @Override
    public void run() {
        try {
            testCaseListener.startSession();
            this.doLogic();
            testCaseListener.endSession();
        } catch (IOException e) {
            logger.fatal("Something went wrong while running CATS: {}", e.getMessage());
            logger.debug("Stacktrace", e);
            exitCodeDueToErrors = 192;
        }
    }

    public void doLogic() throws IOException {
        this.doEarlyOperations();
        OpenAPI openAPI = this.createOpenAPI();
        testCaseListener.initReportingPath();
        this.initGlobalData(openAPI);
        this.startFuzzing(openAPI);
        this.executeCustomFuzzer();
    }

    private void initGlobalData(OpenAPI openAPI) {
        Map<String, Schema> allSchemasFromOpenApi = OpenApiUtils.getSchemas(openAPI, processingArguments.getContentType());
        globalContext.getSchemaMap().putAll(allSchemasFromOpenApi);
        globalContext.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        logger.debug("Schemas: {}", allSchemasFromOpenApi.keySet());
    }

    public void startFuzzing(OpenAPI openAPI) {
        List<String> suppliedPaths = this.matchSuppliedPathsWithContractPaths(openAPI);

        for (Map.Entry<String, PathItem> entry : this.sortPathsAlphabetically(openAPI)) {

            if (suppliedPaths.contains(entry.getKey())) {
                this.fuzzPath(entry, openAPI);
            } else {
                logger.skip("Skipping path {}", entry.getKey());
            }
        }
    }

    private LinkedHashSet<Map.Entry<String, PathItem>> sortPathsAlphabetically(OpenAPI openAPI) {
        return openAPI.getPaths().entrySet()
                .stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void executeCustomFuzzer() throws IOException {
        if (filterArguments.getSuppliedFuzzers().contains(FunctionalFuzzer.class.getSimpleName())) {
            functionalFuzzer.executeCustomFuzzerTests();
            functionalFuzzer.replaceRefData();
        }
    }

    /**
     * Check if there are any supplied paths and match them against the contract
     *
     * @param openAPI the OpenAPI object parsed from the contract
     * @return the list of paths from the contract matching the supplied list
     */
    private List<String> matchSuppliedPathsWithContractPaths(OpenAPI openAPI) {
        List<String> suppliedPaths = this.matchWildCardPaths(filterArguments.getPaths(), openAPI);
        if (filterArguments.getPaths().isEmpty()) {
            suppliedPaths.addAll(openAPI.getPaths().keySet());
        }
        List<String> skipPaths = this.matchWildCardPaths(filterArguments.getSkipPaths(), openAPI);
        suppliedPaths = suppliedPaths.stream().filter(path -> !skipPaths.contains(path)).toList();

        logger.debug("Supplied paths before filtering {}", suppliedPaths);
        suppliedPaths = CatsUtil.filterAndPrintNotMatching(suppliedPaths, path -> openAPI.getPaths().containsKey(path), logger, "Supplied path is not matching the contract {}", Object::toString);
        logger.debug("Supplied paths after filtering {}", suppliedPaths);

        return suppliedPaths;
    }

    private List<String> matchWildCardPaths(List<String> paths, OpenAPI openAPI) {
        Set<String> allContractPaths = openAPI.getPaths().keySet();
        Map<Boolean, List<String>> pathsByWildcard = paths.stream().collect(Collectors.partitioningBy(path -> path.contains("*")));

        List<String> result = new ArrayList<>(pathsByWildcard.get(false));

        for (String wildCardPath : pathsByWildcard.get(true)) {
            result.addAll(allContractPaths
                    .stream()
                    .filter(path -> (wildCardPath.startsWith("*") && path.endsWith(wildCardPath.substring(1))) ||
                            (wildCardPath.endsWith("*") && path.startsWith(wildCardPath.substring(0, wildCardPath.length() - 1)) ||
                                    path.contains(wildCardPath.substring(1, wildCardPath.length() - 1))))
                    .toList());
        }

        logger.debug("Final list of matching wildcard paths: {}", result);
        return result;
    }

    public OpenAPI createOpenAPI() throws IOException {
        String finishMessage = ansi().fgGreen().a("Finished parsing the contract in {} ms").reset().toString();
        long t0 = System.currentTimeMillis();
        OpenAPI openAPI = OpenApiUtils.readOpenApi(apiArguments.getContract());
        logger.complete(finishMessage, (System.currentTimeMillis() - t0));
        return openAPI;
    }

    void doEarlyOperations() throws IOException {
        this.processLogLevelArgument();
        filesArguments.loadConfig();
        apiArguments.validateRequired(spec);
    }

    private void processLogLevelArgument() {
        reportingArguments.processLogData();
    }

    public void fuzzPath(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI) {
        /* WE NEED TO ITERATE THROUGH EACH HTTP OPERATION CORRESPONDING TO THE CURRENT PATH ENTRY*/
        logger.noFormat(" ");
        logger.start("Start fuzzing path {}", pathItemEntry.getKey());
        List<FuzzingData> fuzzingDataList = fuzzingDataFactory.fromPathItem(pathItemEntry.getKey(), pathItemEntry.getValue(), openAPI);

        if (fuzzingDataList.isEmpty()) {
            logger.warning("There was a problem fuzzing path {}. You might want to enable debug mode for more details.", pathItemEntry.getKey());
            return;
        }

        List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered = fuzzingDataList.stream()
                .filter(fuzzingData -> filterArguments.getHttpMethods().contains(fuzzingData.getMethod()))
                .toList();
        Set<HttpMethod> excludedHttpMethods = fuzzingDataList.stream()
                .map(FuzzingData::getMethod)
                .filter(method -> !filterArguments.getHttpMethods().contains(method))
                .collect(Collectors.toSet());

        List<Fuzzer> allFuzzersSorted = filterArguments.getAllRegisteredFuzzers();
        List<String> configuredFuzzers = filterArguments.getFuzzersForPath();

        logger.info("The following HTTP methods won't be executed for path {}: {}", pathItemEntry.getKey(), excludedHttpMethods);
        logger.info("{} configured fuzzers out of {} total fuzzers: {}", configuredFuzzers.size(), (long) allFuzzersSorted.size(), configuredFuzzers);

        /*We only run the fuzzers supplied and exclude those that do not apply for certain HTTP methods*/
        for (Fuzzer fuzzer : allFuzzersSorted) {
            if (configuredFuzzers.contains(fuzzer.toString())) {
                CatsUtil.filterAndPrintNotMatching(fuzzingDataListWithHttpMethodsFiltered, data -> !fuzzer.skipForHttpMethods().contains(data.getMethod()),
                                logger, "HTTP method {} is not supported by {}", t -> t.getMethod().toString(), fuzzer.toString())
                        .forEach(data -> {
                            logger.start("Starting Fuzzer {} ", ansi().fgGreen().a(fuzzer.toString()).reset());
                            logger.debug("Fuzzing payload: {}", data.getPayload());
                            testCaseListener.beforeFuzz(fuzzer.getClass());
                            fuzzer.fuzz(data);
                            testCaseListener.afterFuzz();
                        });
            } else {
                logger.debug("Skipping fuzzer {} for path {} as configured!", fuzzer, pathItemEntry.getKey());
            }
        }
    }

    @Override
    public int getExitCode() {
        return exitCodeDueToErrors + executionStatisticsListener.getErrors();
    }
}
