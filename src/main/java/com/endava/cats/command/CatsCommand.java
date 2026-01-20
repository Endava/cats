package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.exception.CatsException;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.special.FunctionalFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsConfiguration;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.openapi.handler.index.SpecPositionIndex;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.OpenApiRefExtractor;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionChecker;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Inject;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Main application command.
 */
@CommandLine.Command(
        name = "cats",
        mixinStandardHelpOptions = true,
        header = {
                "%n@|green cats - OpenAPI fuzzer and negative testing tool; version ${app.version}|@ %n",
                """
                        # # # # # # # # # # # # # # # # # # # # # # # # # #
                        #             _____   ___ _____ _____             #
                        #            /  __ \\ / _ \\_   _/  ___|            #
                        #            | /  \\// /_\\ \\| | \\ `--.             #
                        #            | |    |  _  || |  `--. \\            #
                        #            | \\__/\\| | | || | /\\__/ /            #
                        #             \\____/\\_| |_/\\_/ \\____/             #
                        #           .. ...    -.-. --- --- .-..           #
                        #                                                 #
                        # # # # # # # # # # # # # # # # # # # # # # # # # #
                        """
        },
        usageHelpAutoWidth = true,
        versionProvider = VersionProvider.class,
        commandListHeading = "%n@|bold,underline Commands:|@%n",
        defaultValueProvider = CommandLine.PropertiesDefaultProvider.class,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        resourceBundle = "version",
        abbreviateSynopsis = true,
        synopsisHeading = "@|bold,underline Usage:|@%n",
        customSynopsis = {"@|bold cats|@ @|fg(yellow) -c|@ <CONTRACT> @|fg(yellow) -s|@ <SERVER> [ADDITIONAL OPTIONS]",
                "@|bold cats (list | replay | run | template | lint | info | stats | validate | random | generate | explain)|@ [OPTIONS]", "%n"},
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command",
                "@|bold ERR|@:Where ERR is the number of errors reported by cats"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Run CATS in blackbox mode and only report 500 http error codes:",
                "    cats -c openapi.yml -s http://localhost:8080 -b -k",
                "", "  Run CATS with authentication headers from an environment variable called TOKEN:",
                "    cats -c openapi.yml -s http://localhost:8080 -H API-Token=$$TOKEN"},
        subcommands = {
                AutoComplete.GenerateCompletion.class,
                CommandLine.HelpCommand.class,
                ListCommand.class,
                ReplayCommand.class,
                RunCommand.class,
                TemplateFuzzCommand.class,
                LintCommand.class,
                InfoCommand.class,
                StatsCommand.class,
                ValidateCommand.class,
                RandomCommand.class,
                GenerateCommand.class,
                ExplainCommand.class
        })
public class CatsCommand implements Runnable, CommandLine.IExitCodeGenerator, AutoCloseable {

    private final PrettyLogger logger;
    private static final String SEPARATOR = "-".repeat(ConsoleUtils.getConsoleColumns(22));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    FuzzingDataFactory fuzzingDataFactory;
    @Inject
    FunctionalFuzzer functionalFuzzer;
    @Inject
    TestCaseListener testCaseListener;

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline API Options:|@%n", exclusive = false)
    ApiArguments apiArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Check Options:|@%n", exclusive = false)
    CheckArguments checkArguments;

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

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Security Fuzzer Options:|@%n", exclusive = false)
    SecurityFuzzerArguments securityFuzzerArguments;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    ExecutionStatisticsListener executionStatisticsListener;

    @Inject
    CatsGlobalContext globalContext;

    @Inject
    SchemaWalker schemaWalker;

    @Inject
    SpecPositionIndex specPositionIndex;

    @Inject
    VersionChecker versionChecker;

    @Getter
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;

    private int exitCodeDueToErrors;

    /**
     * Creates a new instance of CatsCommand.
     */
    public CatsCommand() {
        logger = PrettyLoggerFactory.getLogger(CatsCommand.class);
    }

    @Override
    public void run() {
        try {
            Future<VersionChecker.CheckResult> newVersion = this.checkForNewVersion();
            testCaseListener.startSession();
            this.doLogic();
            this.printSuggestions();
            this.printVersion(newVersion);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (CatsException | IOException | ExecutionException | IllegalArgumentException e) {
            logger.fatal("Something went wrong while running CATS: {}", e.toString());
            logger.debug("Stacktrace: {}", e);
            exitCodeDueToErrors = 192;
        } finally {
            testCaseListener.endSession();
        }
    }

    void printVersion(Future<VersionChecker.CheckResult> newVersion) throws ExecutionException, InterruptedException {
        VersionChecker.CheckResult checkResult = newVersion.get();
        logger.debug("Current version {}. Latest version {}", appVersion, checkResult.getVersion());
        if (checkResult.isNewVersion()) {
            String message = AnsiUtils.boldBrightBlue("A new version is available: {}. Download url: {}");
            String currentVersionFormatted = AnsiUtils.boldBrightBlue(checkResult.getVersion());
            String downloadUrlFormatted = AnsiUtils.boldGreen(checkResult.getDownloadUrl());
            logger.star(message, currentVersionFormatted, downloadUrlFormatted);
        }
    }

    private void doLogic() throws IOException {
        filterArguments.applyProfile(spec);
        this.prepareRun();
        OpenAPI openAPI = this.createOpenAPI();
        this.checkOpenAPI(openAPI);
        apiArguments.validateValidServer(spec, openAPI);

        //reporting path is initialized only if OpenAPI spec is successfully parsed
        testCaseListener.initReportingPath();
        this.printConfiguration(openAPI);
        this.initGlobalData(openAPI);
        this.initSchemaWalker(openAPI);
        testCaseListener.renderFuzzingHeader();
        this.startFuzzing(openAPI);
        this.executeCustomFuzzer();
    }

    private void initSchemaWalker(OpenAPI openAPI) throws IOException {
        if (filterArguments.isLinting()) {
            specPositionIndex.parseSpecs(Path.of(apiArguments.getContract()));
            schemaWalker.initHandlers(openAPI);
        }
    }

    private void checkOpenAPI(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new IllegalArgumentException("Provided OpenAPI specs are invalid!");
        }
    }

    Future<VersionChecker.CheckResult> checkForNewVersion() {
        Callable<VersionChecker.CheckResult> versionCallable = () -> VersionChecker.CheckResult.builder().build();
        if (reportingArguments.isCheckUpdate()) {
            versionCallable = () -> versionChecker.checkForNewVersion(this.appVersion);
        }
        return executor.submit(versionCallable);
    }

    private void printSuggestions() {
        if (executionStatisticsListener.areManyAuthErrors()) {
            String message = AnsiUtils.boldYellow("There were {} tests failing with authorisation errors. Either supply authentication details or check if the supplied credentials are correct!");
            logger.star(message, executionStatisticsListener.getAuthErrors());
        }
        if (executionStatisticsListener.areManyIoErrors()) {
            String message = AnsiUtils.boldYellow("There were {} tests failing with i/o errors. Make sure that you have access to the service or that the --server url is correct!");
            logger.star(message, executionStatisticsListener.getIoErrors());
        }
    }

    private void initGlobalData(OpenAPI openAPI) {
        CatsConfiguration catsConfiguration = new CatsConfiguration(appVersion, apiArguments.getContract(), apiArguments.getServer(), filterArguments.getHttpMethods(),
                filterArguments.getFirstPhaseFuzzersForPath().size() + filterArguments.getSecondPhaseFuzzers().size(), filterArguments.getTotalFuzzersOrLinters(),
                filterArguments.getPathsToRun(openAPI).size(), openAPI.getPaths().size());

        Set<String> refs = Collections.emptySet();
        if (filterArguments.isLinting()) {
            refs = OpenApiRefExtractor.extractRefsFromOpenAPI(apiArguments.getContract());
        }
        globalContext.init(openAPI, processingArguments.getContentType(), filesArguments.getFuzzConfigProperties(), catsConfiguration,
                filesArguments.getErrorLeaksKeywordsList(), refs);

        logger.debug("Fuzzers custom configuration: {}", globalContext.getFuzzersConfiguration());
        logger.debug("Schemas: {}", globalContext.getSchemaMap().keySet());
    }

    void startFuzzing(OpenAPI openAPI) {
        List<String> suppliedPaths = filterArguments.getPathsToRun(openAPI);

        for (Map.Entry<String, PathItem> entry : this.sortPathsAlphabetically(openAPI, filesArguments.getPathsOrder())) {
            if (suppliedPaths.contains(entry.getKey())) {
                this.fuzzPath(entry, openAPI);
            } else {
                logger.skip("Skipping path {}", entry.getKey());
            }
        }
    }

    private Set<Map.Entry<String, PathItem>> sortPathsAlphabetically(OpenAPI openAPI, List<String> pathsOrder) {
        Comparator<Map.Entry<String, PathItem>> customComparator = CatsUtil.createCustomComparatorBasedOnPathsOrder(pathsOrder);

        Set<Map.Entry<String, PathItem>> pathsOrderedAlphabetically = openAPI.getPaths().entrySet()
                .stream().sorted(customComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        logger.debug("Paths ordered alphabetically: {}", pathsOrderedAlphabetically.stream().map(Map.Entry::getKey).toList());

        return pathsOrderedAlphabetically;
    }


    private void executeCustomFuzzer() throws IOException {
        if (filterArguments.getSuppliedFuzzers().contains(FunctionalFuzzer.class.getSimpleName())) {
            functionalFuzzer.executeCustomFuzzerTests();
            functionalFuzzer.replaceRefData();
        }
    }

    OpenAPI createOpenAPI() throws IOException {
        String finishMessage = AnsiUtils.green("Finished parsing the contract in {} ms");
        long t0 = System.currentTimeMillis();
        OpenAPI openAPI = OpenApiUtils.readOpenApi(apiArguments.getContract());
        logger.debug(finishMessage, (System.currentTimeMillis() - t0));
        return openAPI;
    }

    void prepareRun() throws IOException {
        //this is a hack to set terminal width here in order to avoid importing a full-blown library like jline
        // just for getting the terminal width
        CatsRandom.initRandom(processingArguments.getSeed());
        ConsoleUtils.initTerminalWidth(spec);
        reportingArguments.processLogData();
        apiArguments.validateRequired(spec);
        filesArguments.loadConfig();
    }

    private void printConfiguration(OpenAPI openAPI) {
        logger.config(AnsiUtils.bold("OpenAPI specs: {}"), AnsiUtils.blue(apiArguments.getContract()));
        logger.config(AnsiUtils.bold("API base url: {}"), AnsiUtils.blue(apiArguments.getServer()));
        logger.config(AnsiUtils.bold("Reporting path: {}"), AnsiUtils.blue(reportingArguments.getOutputReportFolder()));
        logger.config(AnsiUtils.bold("{} configured fuzzers out of {} total fuzzers"),
                AnsiUtils.boldBlue(filterArguments.getFirstPhaseFuzzersForPath().size()),
                AnsiUtils.boldBlue(filterArguments.getAllRegisteredFuzzers().size()));
        logger.config(AnsiUtils.bold("{} configured paths out of {} total OpenAPI paths"),
                AnsiUtils.boldBlue(filterArguments.getPathsToRun(openAPI).size()),
                AnsiUtils.boldBlue(openAPI.getPaths().size()));
        logger.config(AnsiUtils.bold("HTTP methods in scope: {}"), AnsiUtils.blue(filterArguments.getHttpMethods()));
        logger.config(AnsiUtils.bold("Example flags: useRequestBodyExamples {}, useSchemaExamples {}, usePropertyExamples {}, useResponseBodyExamples {}, useDefaults {}"),
                AnsiUtils.boldBlue(processingArguments.isUseRequestBodyExamples()),
                AnsiUtils.boldBlue(processingArguments.isUseSchemaExamples()),
                AnsiUtils.boldBlue(processingArguments.isUsePropertyExamples()),
                AnsiUtils.boldBlue(processingArguments.isUseResponseBodyExamples()),
                AnsiUtils.boldBlue(processingArguments.isUseDefaults()));
        logger.config(AnsiUtils.bold("selfReferenceDepth {}, largeStringsSize {}, randomHeadersNumber {}, limitFuzzedFields {}, limitXxxOfCombinations {}"),
                AnsiUtils.boldBlue(processingArguments.getSelfReferenceDepth()),
                AnsiUtils.boldBlue(processingArguments.getLargeStringsSize()),
                AnsiUtils.boldBlue(processingArguments.getRandomHeadersNumber()),
                AnsiUtils.boldBlue(processingArguments.getLimitNumberOfFields()),
                AnsiUtils.boldBlue(processingArguments.getLimitXxxOfCombinations()));
        logger.config(AnsiUtils.bold("How the service handles whitespaces and random unicodes: edgeSpacesStrategy {}, sanitizationStrategy {}"),
                AnsiUtils.boldBlue(processingArguments.getEdgeSpacesStrategy()),
                AnsiUtils.boldBlue(processingArguments.getSanitizationStrategy()));
        logger.config(AnsiUtils.bold("Seed value: {}"),
                AnsiUtils.boldBlue(CatsRandom.getStoredSeed()));

        int nofOfOperations = OpenApiUtils.getNumberOfOperations(openAPI);
        logger.config(AnsiUtils.bold("Total number of OpenAPI operations: {}"), AnsiUtils.blue(nofOfOperations));
    }

    private void fuzzPath(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI) {
        /* WE NEED TO ITERATE THROUGH EACH HTTP OPERATION CORRESPONDING TO THE CURRENT PATH ENTRY*/
        String ansiString = AnsiUtils.bold("Start fuzzing path {}");
        logger.start(ansiString, pathItemEntry.getKey());
        List<FuzzingData> fuzzingDataList = fuzzingDataFactory.fromPathItem(pathItemEntry.getKey(), pathItemEntry.getValue(), openAPI);

        if (fuzzingDataList.isEmpty()) {
            logger.warning("There was a problem fuzzing path {}. You might want to enable debug mode for more details. Additionally, you can log a GitHub issue at: https://github.com/Endava/cats/issues.", pathItemEntry.getKey());
            return;
        }

        /* If certain HTTP methods are skipped, we remove the corresponding FuzzingData */
        /* If request uses oneOf/anyOf we only keep the one supplied through --oneOfSelection/--anyOfSelection */
        List<FuzzingData> filteredFuzzingData = fuzzingDataList.stream()
                .filter(fuzzingData -> filterArguments.isHttpMethodSupplied(fuzzingData.getMethod()))
                .filter(fuzzingData -> processingArguments.matchesXxxSelection(fuzzingData.getPayload()))
                .toList();

        Set<HttpMethod> allHttpMethodsFromFuzzingData = filteredFuzzingData
                .stream()
                .map(FuzzingData::getMethod)
                .collect(Collectors.toSet());

        List<Fuzzer> fuzzersToRun = filterArguments.filterOutFuzzersNotMatchingHttpMethodsAndPath(allHttpMethodsFromFuzzingData, pathItemEntry.getKey());
        this.runFuzzers(filteredFuzzingData, fuzzersToRun);
        this.runFuzzers(filteredFuzzingData, filterArguments.getSecondPhaseFuzzers());
    }

    private void runFuzzers(List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered, List<Fuzzer> configuredFuzzers) {
        /*We only run the fuzzers supplied and exclude those that do not apply for certain HTTP methods*/

        for (Fuzzer fuzzer : configuredFuzzers) {
            List<FuzzingData> filteredData = this.filterFuzzingData(fuzzingDataListWithHttpMethodsFiltered, fuzzer);
            filteredData.forEach(data -> runSingleFuzzer(fuzzer, data));
        }
    }

    private void runSingleFuzzer(Fuzzer fuzzer, FuzzingData data) {
        if (data.shouldSkipFuzzerForPath(fuzzer.toString())) {
            logger.skip("Skipping Fuzzer {} for path {} due to OpenAPI extension configuration",
                    AnsiUtils.yellow(fuzzer.toString()), data.getPath());
            return;
        }

        logFuzzerStart(fuzzer, data);

        if (!(fuzzer instanceof FunctionalFuzzer)) {
            testCaseListener.beforeFuzz(fuzzer.getClass(), data.getContractPath(), data.getMethod().name());
        }

        fuzzer.fuzz(data);

        if (!(fuzzer instanceof FunctionalFuzzer)) {
            testCaseListener.afterFuzz(data.getContractPath());
        }

        logFuzzerEnd(fuzzer, data);
    }

    private void logFuzzerEnd(Fuzzer fuzzer, FuzzingData data) {
        logger.complete("Finishing Fuzzer {}, http method {}, path {}", AnsiUtils.green(fuzzer.toString()), data.getMethod(), data.getPath());
        logger.info("{}", SEPARATOR);
    }

    private void logFuzzerStart(Fuzzer fuzzer, FuzzingData data) {
        logger.start("Starting Fuzzer {}, http method {}, path {}", AnsiUtils.green(fuzzer.toString()), data.getMethod(), data.getPath());
        logger.debug("Fuzzing payload: {}", data.getPayload());
    }

    private List<FuzzingData> filterFuzzingData(List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered, Fuzzer fuzzer) {
        return CatsUtil.filterAndPrintNotMatching(
                fuzzingDataListWithHttpMethodsFiltered,
                data -> !fuzzer.skipForHttpMethods().contains(data.getMethod()),
                logger,
                "HTTP method {} is not supported by {}",
                t -> t.getMethod().toString(), fuzzer.toString());
    }

    @Override
    public int getExitCode() {
        return exitCodeDueToErrors + executionStatisticsListener.getErrors();
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
