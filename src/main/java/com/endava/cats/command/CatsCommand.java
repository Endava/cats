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
import org.fusesource.jansi.Ansi;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
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
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

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
public class CatsCommand implements Runnable, CommandLine.IExitCodeGenerator {

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
            testCaseListener.endSession();
            this.printSuggestions();
            this.printVersion(newVersion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (CatsException | IOException | ExecutionException | IllegalArgumentException e) {
            logger.fatal("Something went wrong while running CATS: {}", e.toString());
            logger.debug("Stacktrace: {}", e);
            exitCodeDueToErrors = 192;
        }
    }

    void printVersion(Future<VersionChecker.CheckResult> newVersion) throws ExecutionException, InterruptedException {
        VersionChecker.CheckResult checkResult = newVersion.get();
        logger.debug("Current version {}. Latest version {}", appVersion, checkResult.getVersion());
        if (checkResult.isNewVersion()) {
            String message = ansi()
                    .bold()
                    .fgBrightBlue()
                    .a("A new version is available: {}. Download url: {}")
                    .reset()
                    .toString();
            String currentVersionFormatted = ansi()
                    .bold()
                    .fgBrightBlue()
                    .a(checkResult.getVersion())
                    .reset()
                    .bold()
                    .fgBrightBlue()
                    .toString();
            String downloadUrlFormatted = ansi()
                    .bold()
                    .fgGreen()
                    .a(checkResult.getDownloadUrl())
                    .reset()
                    .toString();
            logger.star(message, currentVersionFormatted, downloadUrlFormatted);
        }
    }

    private void doLogic() throws IOException {
        this.prepareRun();
        OpenAPI openAPI = this.createOpenAPI();
        this.checkOpenAPI(openAPI);
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
            String message = ansi()
                    .bold()
                    .fgBrightYellow()
                    .a("There were {} tests failing with authorisation errors. Either supply authentication details or check if the supplied credentials are correct!")
                    .reset()
                    .toString();
            logger.star(message, executionStatisticsListener.getAuthErrors());
        }
        if (executionStatisticsListener.areManyIoErrors()) {
            String message = ansi()
                    .bold()
                    .fgBrightYellow()
                    .a("There were {} tests failing with i/o errors. Make sure that you have access to the service or that the --server url is correct!")
                    .reset()
                    .toString();
            logger.star(message, executionStatisticsListener.getIoErrors());
        }
    }

    private void initGlobalData(OpenAPI openAPI) {
        CatsConfiguration catsConfiguration = new CatsConfiguration(appVersion, apiArguments.getContract(), apiArguments.getServer(), filterArguments.getHttpMethods(),
                filterArguments.getFirstPhaseFuzzersForPath().size() + filterArguments.getSecondPhaseFuzzers().size(), filterArguments.getTotalFuzzersOrLinters(),
                filterArguments.getPathsToRun(openAPI).size(), openAPI.getPaths().size());

        Set<String> refs = OpenApiRefExtractor.extractRefsFromOpenAPI(apiArguments.getContract());
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
        String finishMessage = ansi().fgGreen().a("Finished parsing the contract in {} ms").reset().toString();
        long t0 = System.currentTimeMillis();
        OpenAPI openAPI = OpenApiUtils.readOpenApi(apiArguments.getContract());
        logger.debug(finishMessage, (System.currentTimeMillis() - t0));
        return openAPI;
    }

    void prepareRun() throws IOException {
        //this is a hack to set terminal width here in order to avoid importing a full-blown library like jline
        // just for getting the terminal width
        ConsoleUtils.initTerminalWidth(spec);
        reportingArguments.processLogData();
        apiArguments.validateRequired(spec);
        apiArguments.validateValidServer(spec);
        filesArguments.loadConfig();
    }

    private void printConfiguration(OpenAPI openAPI) {
        logger.config(ansi().bold().a("OpenAPI specs: {}").reset().toString(), ansi().fg(Ansi.Color.BLUE).a(apiArguments.getContract()).reset());
        logger.config(ansi().bold().a("API base url: {}").reset().toString(), ansi().fg(Ansi.Color.BLUE).a(apiArguments.getServer()).reset());
        logger.config(ansi().bold().a("Reporting path: {}").reset().toString(), ansi().fg(Ansi.Color.BLUE).a(reportingArguments.getOutputReportFolder()).reset());
        logger.config(ansi().bold().a("{} configured fuzzers out of {} total fuzzers").bold().reset().toString(),
                ansi().fg(Ansi.Color.BLUE).a(filterArguments.getFirstPhaseFuzzersForPath().size()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(filterArguments.getAllRegisteredFuzzers().size()).reset().bold());
        logger.config(ansi().bold().a("{} configured paths out of {} total OpenAPI paths").bold().reset().toString(),
                ansi().fg(Ansi.Color.BLUE).a(filterArguments.getPathsToRun(openAPI).size()).bold().reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(openAPI.getPaths().size()).reset().bold());
        logger.config(ansi().bold().a("HTTP methods in scope: {}").reset().toString(), ansi().fg(Ansi.Color.BLUE).a(filterArguments.getHttpMethods()).reset());
        logger.config(ansi().bold().bold().a("Example flags: useRequestBodyExamples {}, useSchemaExamples {}, usePropertyExamples {}, useResponseBodyExamples {}, useDefaults {}").reset().toString(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.isUseRequestBodyExamples()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.isUseSchemaExamples()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.isUsePropertyExamples()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.isUseResponseBodyExamples()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.isUseDefaults()).reset().bold());
        logger.config(ansi().bold().a("selfReferenceDepth {}, largeStringsSize {}, randomHeadersNumber {}, limitFuzzedFields {}, limitXxxOfCombinations {}").reset().toString(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getSelfReferenceDepth()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getLargeStringsSize()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getRandomHeadersNumber()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getLimitNumberOfFields()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getLimitXxxOfCombinations()).reset().bold());
        logger.config(ansi().bold().a("How the service handles whitespaces and random unicodes: edgeSpacesStrategy {}, sanitizationStrategy {}").reset().toString(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getEdgeSpacesStrategy()).reset().bold(),
                ansi().fg(Ansi.Color.BLUE).a(processingArguments.getSanitizationStrategy()).reset().bold());

        int nofOfOperations = OpenApiUtils.getNumberOfOperations(openAPI);
        logger.config(ansi().bold().a("Total number of OpenAPI operations: {}").reset().toString(), ansi().fg(Ansi.Color.BLUE).a(nofOfOperations));
    }

    private void fuzzPath(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI) {
        /* WE NEED TO ITERATE THROUGH EACH HTTP OPERATION CORRESPONDING TO THE CURRENT PATH ENTRY*/
        String ansiString = ansi().bold().a("Start fuzzing path {}").reset().toString();
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
        logger.complete("Finishing Fuzzer {}, http method {}, path {}", ansi().fgGreen().a(fuzzer.toString()).reset(), data.getMethod(), data.getPath());
        logger.info("{}", SEPARATOR);
    }

    private void logFuzzerStart(Fuzzer fuzzer, FuzzingData data) {
        logger.start("Starting Fuzzer {}, http method {}, path {}", ansi().fgGreen().a(fuzzer.toString()).reset(), data.getMethod(), data.getPath());
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
}
