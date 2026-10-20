package com.endava.cats.command;

import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.auth.wfc.WfcAuthProvider;
import com.endava.cats.command.model.CommandContext;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.exception.CatsException;
import com.endava.cats.exception.CatsExecutionCancelledException;
import com.endava.cats.exception.CatsExecutionLimitReachedException;
import com.endava.cats.execution.ExecutionStopController;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFuzzer;
import com.endava.cats.fuzzer.special.RandomFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsConfiguration;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.openapi.handler.index.SpecPositionIndex;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.UnresolvedParametersSummary;
import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.event.CatsExecutionEventPublisher;
import com.endava.cats.tui.CatsTuiLauncher;
import com.endava.cats.tui.model.RunConfigurationSnapshot;
import com.endava.cats.util.AnnotationUtils;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiRefExtractor;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.SensitiveDataPolicy;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
        resourceBundle = "version",
        abbreviateSynopsis = true,
        synopsisHeading = "@|bold,underline Usage:|@%n",
        customSynopsis = {"@|bold cats|@ @|fg(yellow) -c|@ <CONTRACT> @|fg(yellow) -s|@ <SERVER> [ADDITIONAL OPTIONS]",
                "@|bold cats (list | replay | run | template | lint | info | stats | validate | random | generate | explain)|@ [OPTIONS]", "%n"},
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold 0|@:Successful program execution",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Internal execution error: an exception occurred when executing command",
                "@|bold 130|@:Execution cancelled from the terminal interface"},
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

    static final int CANCELLED_EXIT_CODE = 130;
    private final PrettyLogger logger;
    private static final String SEPARATOR = "-".repeat(ConsoleUtils.getConsoleColumns(22));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    FuzzingDataFactory fuzzingDataFactory;
    @Inject
    FunctionalFuzzer functionalFuzzer;
    @Inject
    TestCaseListener testCaseListener;
    @Inject
    CatsExecutionEventPublisher executionEventPublisher;
    @Inject
    CatsTuiLauncher tuiLauncher;
    @Inject
    ServiceCaller serviceCaller;
    @Inject
    ExecutionStopController executionStopController;

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
    RuntimeResourcePool runtimeResourcePool;

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

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Quality Gate Options:|@%n", exclusive = false)
    QualityGateArguments qualityGateArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Stop Options:|@%n", exclusive = false)
    StopArguments stopArguments;

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

    @Inject
    WfcAuthProvider wfcAuthProvider;

    @Inject
    RandomFuzzer randomFuzzer;

    @Getter
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;

    static final Set<String> COVERAGE_WAVE_FUZZERS = Set.of(
            "happypath", "onlyrequiredfields", "defaultvaluesinfields", "examplesfields",
            "emptyjsonbody", "numericvaluesinstringfields", "removefields",
            "alternativedatetimeformatsfields", "queryobjectasrequestbody");
    private static final List<String> COMBINATORIAL_COVERAGE_FUZZERS = List.of(
            "removefields", "numericvaluesinstringfields");
    static final Set<String> CRASH_WAVE_FUZZERS = Set.of(
            "nullelementsinarrayfields", "smallnegativeandint32boundaryfields",
            "extremenegativenumbersinintegerfields", "extremepositivenumbersinintegerfields",
            "extremenegativenumbersindecimalfields", "extremepositivenumbersindecimalfields",
            "zerointegerbody", "zerodecimalbody",
            "minlengthexactvaluesinstringfields", "maxlengthexactvaluesinstringfields",
            "invalidvaluesinenumsfields", "epochnumberindatetimefields",
            "stringformatalmostvalidvalues", "stringformattotallywrongvalues",
            "sqlinjectioninstringfields", "nosqlinjectioninstringfields",
            "commandinjectioninstringfields", "sstiinjectioninstringfields",
            "xssinjectioninstringfields", "bypassauthentication",
            "insecuredirectobjectreferences", "massassignment",
            "httpmethods", "dummyrequest", "malformedjson",
            "randomresources", "leadingcontrolcharsinfields",
            "trailingcontrolcharsinfields", "onlycontrolcharsinfields",
            "zalgotextinfields", "abugidasinstringfields",
            "lowercaseexpandingbytesinstringfields", "uppercaseexpandingbytesinstringfields");
    private static final int MAX_CONTINUOUS_RANDOM_ROUNDS = 200;

    private int exitCodeDueToErrors = CommandLine.ExitCode.OK;
    private final List<DeferredHappyPathDelete> deferredHappyPathDeletes = new ArrayList<>();

    private record DeferredHappyPathDelete(List<FuzzingData> pathData, List<FuzzingData> deleteData,
                                           List<Fuzzer> deleteFuzzers) {
    }

    private final class PreparedPath {
        private final String pathKey;
        private final PathItem pathItem;
        private final OpenAPI openAPI;
        private final boolean binaryStreamOnly;
        private List<FuzzingData> filteredFuzzingData;
        private List<Fuzzer> fuzzersToRun;
        private boolean initialized;

        PreparedPath(String pathKey, PathItem pathItem, OpenAPI openAPI, boolean binaryStreamOnly) {
            this.pathKey = pathKey;
            this.pathItem = pathItem;
            this.openAPI = openAPI;
            this.binaryStreamOnly = binaryStreamOnly;
        }

        String pathKey() {
            return pathKey;
        }

        boolean binaryStreamOnly() {
            return binaryStreamOnly;
        }

        private void ensureInitialized() {
            if (initialized) {
                return;
            }
            initialized = true;
            List<FuzzingData> fuzzingDataList = fuzzingDataFactory.fromPathItem(pathKey, pathItem, openAPI);
            if (fuzzingDataList.isEmpty()) {
                logger.warning("There was a problem fuzzing path {}. You might want to enable debug mode for more details. Additionally, you can log a GitHub issue at: https://github.com/Endava/cats/issues.", pathKey);
                this.filteredFuzzingData = List.of();
                this.fuzzersToRun = List.of();
                return;
            }
            this.filteredFuzzingData = fuzzingDataList.stream()
                    .filter(fuzzingData -> filterArguments.isHttpMethodSupplied(fuzzingData.getMethod()))
                    .filter(fuzzingData -> processingArguments.matchesXxxSelection(fuzzingData.getPayload()))
                    .toList();
            Set<HttpMethod> allHttpMethodsFromFuzzingData = filteredFuzzingData.stream()
                    .map(FuzzingData::getMethod)
                    .collect(Collectors.toSet());
            this.fuzzersToRun = filterArguments.filterOutFuzzersNotMatchingHttpMethodsAndPath(
                    allHttpMethodsFromFuzzingData, pathKey);
        }

        List<FuzzingData> filteredFuzzingData() {
            ensureInitialized();
            return filteredFuzzingData;
        }

        List<Fuzzer> fuzzersToRun() {
            ensureInitialized();
            return fuzzersToRun;
        }
    }

    /**
     * Creates a new instance of CatsCommand.
     */
    public CatsCommand() {
        logger = PrettyLoggerFactory.getLogger(CatsCommand.class);
    }

    /**
     * Run with explicit context (used by subcommands).
     * This is the preferred method for subcommands to use.
     * Applies context properties to arguments before running normal execution.
     *
     * @param context the command context containing configuration
     */
    public void runWithContext(CommandContext context) {
        // Apply context to arguments
        context.getFuzzerType().ifPresent(filterArguments::customFilter);
        context.getCustomFuzzerFile().ifPresent(filesArguments::setCustomFuzzerFile);
        context.getSecurityFuzzerFile().ifPresent(filesArguments::setSecurityFuzzerFile);
        context.getHeadersFile().ifPresent(filesArguments::setHeadersFile);
        context.getHeadersMap().ifPresent(filesArguments::setHeadersMap);
        context.getCreateRefData().ifPresent(filesArguments::setCreateRefData);
        context.getRefDataFile().ifPresent(filesArguments::setRefDataFile);
        context.getQueryFile().ifPresent(filesArguments::setQueryFile);
        context.getContentType().ifPresent(processingArguments::setContentType);
        context.getXxxOfSelections().ifPresent(processingArguments::setXxxOfSelections);
        context.getDiscriminatorCasing().ifPresent(processingArguments::setDiscriminatorCasing);
        context.getSeed().ifPresent(processingArguments::setSeed);
        context.getPaths().ifPresent(filterArguments::setPaths);
        context.getHttpMethods().ifPresent(methods -> filterArguments.setHttpMethods(methods));
        context.getSkipFuzzers().ifPresent(filterArguments::setSkipFuzzers);
        context.getSkipPaths().ifPresent(skipPaths -> filterArguments.getSkipPaths().addAll(skipPaths));
        context.getIncludeContract().ifPresent(include -> {
            if (filterArguments.getCheckArguments() != null) {
                filterArguments.getCheckArguments().setIncludeContract(include);
            }
        });
        context.getContract().ifPresent(apiArguments::setContract);
        context.getServer().ifPresent(apiArguments::setServer);
        context.getLimitXxxOfCombinations().ifPresent(processingArguments::setLimitXxxOfCombinations);
        context.getFilesArguments().ifPresent(fa -> filesArguments = (FilesArguments) fa);
        context.getTotalCountType().ifPresent(filterArguments::setTotalCountType);

        // Run normal execution
        this.run();
    }

    @Override
    public void run() {
        if (reportingArguments.isTui()) {
            if (filterArguments.isDryRun()) {
                logger.fatal("--tui cannot be combined with --dryRun");
                exitCodeDueToErrors = CommandLine.ExitCode.USAGE;
                return;
            }
            if (reportingArguments.getTuiMaxResults() < 1) {
                logger.fatal("--tuiMaxResults must be greater than zero");
                exitCodeDueToErrors = CommandLine.ExitCode.USAGE;
                return;
            }
            reportingArguments.processLogData();
            RuntimeException terminalFailure = null;
            try {
                if (tuiLauncher.run(this::executeSession, serviceCaller::cancelActiveCalls,
                        reportingArguments.getTuiMaxResults())) {
                    exitCodeDueToErrors = CANCELLED_EXIT_CODE;
                }
            } catch (RuntimeException e) {
                terminalFailure = e;
                exitCodeDueToErrors = CommandLine.ExitCode.SOFTWARE;
            } finally {
                reportingArguments.restoreLogDataAfterTui();
            }
            if (terminalFailure != null) {
                logger.fatal("Unable to run the CATS terminal interface: {}", terminalFailure.getMessage());
            }
            return;
        }
        executeSession();
    }

    private void executeSession() {
        boolean fuzzingCompleted = false;
        try {
            testCaseListener.startSession();
            Future<VersionChecker.CheckResult> newVersion = this.checkForNewVersion();
            executionStopController.startSession();
            this.doLogic();
            fuzzingCompleted = true;
            this.printVersion(newVersion);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            executionStatisticsListener.markCancelled("Execution cancelled by user");
            exitCodeDueToErrors = CANCELLED_EXIT_CODE;
        } catch (CatsExecutionCancelledException e) {
            executionStatisticsListener.markCancelled(e.getMessage());
            exitCodeDueToErrors = CANCELLED_EXIT_CODE;
        } catch (CatsExecutionLimitReachedException e) {
            executionStatisticsListener.markLimitReached(e.getMessage());
            logger.complete(e.getMessage());
        } catch (CatsException | IOException | ExecutionException | IllegalArgumentException e) {
            executionStatisticsListener.markFailed(e.toString());
            logger.fatal("Something went wrong while running CATS: {}", e.toString());
            logger.debug("Stacktrace: {}", e);
            exitCodeDueToErrors = CommandLine.ExitCode.SOFTWARE;
        } catch (RuntimeException e) {
            executionStatisticsListener.markFailed(e.toString());
            throw e;
        } finally {
            executionStopController.finishSession();
            ExecutionSummary executionSummary = testCaseListener.endSession();
            if (fuzzingCompleted) {
                this.printSuggestions();
                this.printUnresolvedParameters();
            }
            if (executionSummary != null && executionSummary.outcome().status() == RunOutcome.Status.FAILED) {
                exitCodeDueToErrors = CommandLine.ExitCode.SOFTWARE;
            }
            if (executionSummary != null && executionEventPublisher.hasSubscribers()) {
                executionEventPublisher.publish(new CatsExecutionEvent.SessionEnded(Instant.now(), executionSummary));
            }
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
        this.prepareRun();
        CatsExecutionCancelledException.check();
        OpenAPI openAPI = this.createOpenAPI();
        CatsExecutionCancelledException.check();
        this.checkOpenAPI(openAPI);
        apiArguments.validateValidServer(spec, openAPI);
        filterArguments.validateValidPaths(openAPI);
        //reporting path is initialized only if OpenAPI spec is successfully parsed
        testCaseListener.initReportingPath();
        reportingArguments.registerSensitiveQueryParams(SensitiveDataPolicy.openApiQueryApiKeyNames(openAPI));
        this.printConfiguration(openAPI);
        this.initGlobalData(openAPI);
        this.initSchemaWalker(openAPI);
        testCaseListener.renderFuzzingHeader();
        this.startFuzzing(openAPI);
        CatsExecutionCancelledException.check();
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

    /**
     * Prints hints about auth and i/o errors. This runs after {@link TestCaseListener#endSession()} because
     * the {@code star} log level is only enabled at that point when running with the default summary verbosity.
     */
    void printSuggestions() {
        try {
            if (executionStatisticsListener.areManyAuthErrors()) {
                String message = AnsiUtils.boldYellow("There were {} tests failing with authorisation errors. Either supply authentication details or check if the supplied credentials are correct!");
                logger.star(message, executionStatisticsListener.getAuthErrors());
            }
            if (executionStatisticsListener.areManyIoErrors()) {
                String message = AnsiUtils.boldYellow("There were {} tests failing with i/o errors. Make sure that you have access to the service or that the --server url is correct!");
                logger.star(message, executionStatisticsListener.getIoErrors());
            }
        } catch (RuntimeException e) {
            logger.debug("Unable to print suggestions", e);
        }
    }

    void printUnresolvedParameters() {
        if (!reportingArguments.isDebug()) {
            return;
        }
        try {
            OpenAPI openAPI = globalContext.getOpenAPI();
            List<RuntimeResourcePool.UnresolvedOperation> operations = runtimeResourcePool.unresolvedOperations().stream()
                    .filter(operation -> isDocumentedOperation(openAPI, operation.outcome().path(), operation.outcome().method()))
                    .toList();
            List<String> lines = UnresolvedParametersSummary.render(operations);
            if (lines.isEmpty()) {
                return;
            }
            ConsoleUtils.emptyLine();
            logger.info("{}", lines.getFirst());
            lines.stream().skip(1).forEach(line -> logger.noFormat("{}", line));
        } catch (RuntimeException e) {
            logger.debug("Unable to print unresolved parameters summary", e);
        }
    }

    private static boolean isDocumentedOperation(OpenAPI openAPI, String path, HttpMethod method) {
        PathItem pathItem = Optional.ofNullable(openAPI).map(OpenAPI::getPaths).map(paths -> paths.get(path)).orElse(null);
        return pathItem != null && pathItem.readOperationsMap().keySet().stream()
                .anyMatch(documentedMethod -> documentedMethod.name().equals(method.name()));
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
        executionEventPublisher.publish(new CatsExecutionEvent.ConfigurationLoaded(Instant.now(),
                RunConfigurationSnapshot.from(catsConfiguration)));

        logger.debug("Fuzzers custom configuration: {}", globalContext.getFuzzersConfiguration());
        logger.debug("Schemas: {}", globalContext.getSchemaMap().keySet());
    }

    void startFuzzing(OpenAPI openAPI) {
        List<String> suppliedPaths = filterArguments.getPathsToRun(openAPI);
        deferredHappyPathDeletes.clear();
        List<Map.Entry<String, PathItem>> paths = new ArrayList<>(this.sortPathsAlphabetically(openAPI, filesArguments.getPathsOrder()));
        if (processingArguments.isReuseSuccessfulResources() && filesArguments.getPathsOrder().isEmpty() &&
                filterArguments.isHttpMethodSupplied(HttpMethod.POST)) {
            paths = OperationDependencyOrder.order(openAPI, paths);
        }
        this.preseedCollectionGets(paths, suppliedPaths, openAPI);

        if (!processingArguments.isReuseSuccessfulResources() || filterArguments.isDryRun()) {
            if (randomFuzzer != null) {
                randomFuzzer.setMultipleOperationsInScope(suppliedPaths.size() > 1);
            }
            try {
                for (Map.Entry<String, PathItem> entry : paths) {
                    CatsExecutionCancelledException.check();
                    if (suppliedPaths.contains(entry.getKey())) {
                        this.fuzzPath(entry, openAPI);
                    } else {
                        logger.skip("Skipping path {}", entry.getKey());
                    }
                }
                this.runDeferredHappyPathDeletes();
            } finally {
                if (randomFuzzer != null) {
                    randomFuzzer.setMultipleOperationsInScope(false);
                }
            }
            return;
        }

        List<PreparedPath> preparedPaths = preparePathsForExecution(paths, suppliedPaths, openAPI);
        if (randomFuzzer != null) {
            randomFuzzer.setMultipleOperationsInScope(preparedPaths.size() > 1);
        }

        try {
            runPhasedWavesAcrossPaths(preparedPaths);
            runBinaryStreamPaths(preparedPaths, true);
            this.runDeferredHappyPathDeletes();
            runBinaryStreamPaths(preparedPaths, false);
            this.runContinuousRandomUntilTimeLimit(preparedPaths);
        } catch (CatsExecutionLimitReachedException e) {
            this.runDeferredHappyPathDeletes();
            throw e;
        } finally {
            if (randomFuzzer != null) {
                randomFuzzer.setMultipleOperationsInScope(false);
            }
        }
    }

    private List<PreparedPath> preparePathsForExecution(List<Map.Entry<String, PathItem>> paths,
                                                        List<String> suppliedPaths, OpenAPI openAPI) {
        List<PreparedPath> preparedPaths = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : paths) {
            CatsExecutionCancelledException.check();
            if (!suppliedPaths.contains(entry.getKey())) {
                logger.skip("Skipping path {}", entry.getKey());
                continue;
            }
            boolean binaryStreamOnly = entry.getValue().getGet() != null &&
                    entry.getValue().readOperationsMap().size() == 1 &&
                    isBinaryStreamOnlyResponse(entry.getValue().getGet());
            preparedPaths.add(new PreparedPath(entry.getKey(), entry.getValue(), openAPI, binaryStreamOnly));
        }
        return preparedPaths;
    }

    /**
     * Executes configured fuzzers in breadth-first waves across all prepared paths:
     * <ol>
     *   <li>Seed wave ({@code HappyPath} and {@code OnlyRequiredFields}) so runtime resources and baselines
     *       are populated across all endpoints before negative mutations run.</li>
     *   <li>Remaining coverage-wave fuzzers across all paths.</li>
     *   <li>Crash-wave fuzzers across all paths.</li>
     *   <li>All remaining first-phase fuzzers and path-level second-phase fuzzers.</li>
     * </ol>
     */
    private void runPhasedWavesAcrossPaths(List<PreparedPath> preparedPaths) {
        for (PreparedPath path : preparedPaths) {
            if (!path.binaryStreamOnly()) {
                CatsExecutionCancelledException.check();
                if (path.filteredFuzzingData().isEmpty() && path.fuzzersToRun().isEmpty()) {
                    continue;
                }
                logger.start(AnsiUtils.bold("Start fuzzing path {}"), path.pathKey());
                executionEventPublisher.publish(new CatsExecutionEvent.PathStarted(Instant.now(), path.pathKey()));
                runSeedFuzzersForPath(path.filteredFuzzingData(), path.fuzzersToRun());
            }
        }
        long resourcesBeforeCoverageWave = runtimeResourcePool != null ? runtimeResourcePool.observedResourceCount() : 0;
        for (PreparedPath path : preparedPaths) {
            if (path.binaryStreamOnly()) {
                continue;
            }
            CatsExecutionCancelledException.check();
            List<Fuzzer> fastCoverage = nonSeedFuzzers(path.fuzzersToRun()).stream()
                    .filter(fuzzer -> isInWave(fuzzer, COVERAGE_WAVE_FUZZERS) &&
                            !isInWave(fuzzer, Set.copyOf(COMBINATORIAL_COVERAGE_FUZZERS)))
                    .toList();
            runFuzzers(path.filteredFuzzingData(), fastCoverage);
        }
        for (String combinatorialName : COMBINATORIAL_COVERAGE_FUZZERS) {
            Set<String> singleWave = Set.of(combinatorialName);
            for (PreparedPath path : preparedPaths) {
                if (path.binaryStreamOnly()) {
                    continue;
                }
                CatsExecutionCancelledException.check();
                List<Fuzzer> combinatorialCoverage = nonSeedFuzzers(path.fuzzersToRun()).stream()
                        .filter(fuzzer -> isInWave(fuzzer, singleWave))
                        .toList();
                runFuzzers(path.filteredFuzzingData(), combinatorialCoverage);
            }
        }
        if (runtimeResourcePool != null && runtimeResourcePool.observedResourceCount() > resourcesBeforeCoverageWave) {
            retryUnresolvedSeedOperations(preparedPaths);
        }
        for (PreparedPath path : preparedPaths) {
            if (path.binaryStreamOnly()) {
                continue;
            }
            CatsExecutionCancelledException.check();
            List<Fuzzer> crashWave = nonSeedFuzzers(path.fuzzersToRun()).stream()
                    .filter(fuzzer -> !isInWave(fuzzer, COVERAGE_WAVE_FUZZERS) && isInWave(fuzzer, CRASH_WAVE_FUZZERS))
                    .toList();
            runFuzzers(path.filteredFuzzingData(), crashWave);
        }
        for (PreparedPath path : preparedPaths) {
            if (path.binaryStreamOnly() || (path.filteredFuzzingData().isEmpty() && path.fuzzersToRun().isEmpty())) {
                continue;
            }
            CatsExecutionCancelledException.check();
            List<Fuzzer> remainingWave = nonSeedFuzzers(path.fuzzersToRun()).stream()
                    .filter(fuzzer -> !isInWave(fuzzer, COVERAGE_WAVE_FUZZERS) && !isInWave(fuzzer, CRASH_WAVE_FUZZERS))
                    .toList();
            runFuzzers(path.filteredFuzzingData(), remainingWave);
            this.runSecondPhaseFuzzers(path.filteredFuzzingData(), SecondPhaseFuzzer.Trigger.PATH_COMPLETED);
            executionEventPublisher.publish(new CatsExecutionEvent.PathCompleted(Instant.now(), path.pathKey()));
        }
    }

    private void retryUnresolvedSeedOperations(List<PreparedPath> preparedPaths) {
        if (runtimeResourcePool == null) {
            return;
        }
        for (PreparedPath path : preparedPaths) {
            if (path.binaryStreamOnly()) {
                continue;
            }
            Fuzzer happyPath = path.fuzzersToRun().stream()
                    .filter(HappyPathFuzzer.class::isInstance)
                    .findFirst()
                    .orElse(null);
            if (happyPath == null) {
                continue;
            }
            List<FuzzingData> unresolvedSeedData = path.filteredFuzzingData().stream()
                    .filter(this::isSafeResourceSeed)
                    .filter(data -> !runtimeResourcePool.hasSucceededWithUnmodifiedRequest(data.getPath(), data.getMethod()))
                    .sorted(Comparator.comparingInt(data -> seedMethodOrder(data.getMethod())))
                    .toList();
            if (!unresolvedSeedData.isEmpty()) {
                runFuzzers(unresolvedSeedData, List.of(happyPath));
            }
        }
    }

    private void runBinaryStreamPaths(List<PreparedPath> preparedPaths, boolean parameterized) {
        for (PreparedPath path : preparedPaths) {
            if (!path.binaryStreamOnly() || path.pathKey().contains("{") != parameterized) {
                continue;
            }
            CatsExecutionCancelledException.check();
            logger.start(AnsiUtils.bold("Start fuzzing path {}"), path.pathKey());
            executionEventPublisher.publish(new CatsExecutionEvent.PathStarted(Instant.now(), path.pathKey()));
            this.runFirstPhaseFuzzers(path.filteredFuzzingData(), path.fuzzersToRun());
            this.runSecondPhaseFuzzers(path.filteredFuzzingData(), SecondPhaseFuzzer.Trigger.PATH_COMPLETED);
            executionEventPublisher.publish(new CatsExecutionEvent.PathCompleted(Instant.now(), path.pathKey()));
        }
    }

    private void runContinuousRandomUntilTimeLimit(List<PreparedPath> preparedPaths) {
        if (!shouldRunContinuousRandom()) {
            return;
        }
        List<FuzzingData> nonEmptyData = preparedPaths.stream()
                .flatMap(path -> path.filteredFuzzingData().stream())
                .filter(data -> !JsonUtils.isEmptyPayload(data.getPayload()))
                .toList();
        if (nonEmptyData.isEmpty()) {
            return;
        }
        for (int round = 0; round < MAX_CONTINUOUS_RANDOM_ROUNDS; round++) {
            for (FuzzingData data : nonEmptyData) {
                CatsExecutionCancelledException.check();
                runSingleFuzzer(randomFuzzer, data, randomFuzzer::fuzzContinuous);
            }
        }
    }

    private boolean shouldRunContinuousRandom() {
        if (stopArguments == null || stopArguments.getStopAfterTimeInSec() <= 0 ||
                filterArguments.isDryRun() || reportingArguments.isTui() || randomFuzzer == null) {
            return false;
        }
        boolean skipped = Optional.ofNullable(filterArguments.getSkipFuzzers()).orElse(List.of()).stream()
                .anyMatch(name -> "randomfuzzer".equalsIgnoreCase(name) || "random".equalsIgnoreCase(name));
        if (skipped) {
            return false;
        }
        List<String> supplied = Optional.ofNullable(filterArguments.getSuppliedFuzzers()).orElse(List.of());
        return supplied.isEmpty() ||
                supplied.stream().anyMatch(name -> "randomfuzzer".equalsIgnoreCase(name) || "random".equalsIgnoreCase(name));
    }

    private static boolean isSeedFuzzer(Fuzzer fuzzer) {
        return fuzzer instanceof HappyPathFuzzer || fuzzer instanceof OnlyRequiredFieldsFuzzer;
    }

    private static List<Fuzzer> nonSeedFuzzers(List<Fuzzer> configuredFuzzers) {
        if (!configuredFuzzers.stream().anyMatch(HappyPathFuzzer.class::isInstance)) {
            return configuredFuzzers;
        }
        return configuredFuzzers.stream().filter(fuzzer -> !isSeedFuzzer(fuzzer)).toList();
    }

    private boolean isInWave(Fuzzer fuzzer, Set<String> waveLowerNames) {
        String sanitized = ConsoleUtils.removeTrimSanitize(fuzzer.toString()).toLowerCase(Locale.ROOT);
        String simple = fuzzer.getClass().getSimpleName().replace("Fuzzer", "").toLowerCase(Locale.ROOT);
        return waveLowerNames.contains(sanitized) || waveLowerNames.contains(simple);
    }

    /**
     * Optionally calls a bounded number of selected, unparameterized GETs before normal path fuzzing. Their
     * collection responses can supply IDs to operations that otherwise run before the producing GET path.
     */
    private void preseedCollectionGets(List<Map.Entry<String, PathItem>> paths, List<String> suppliedPaths, OpenAPI openAPI) {
        if (!processingArguments.isPreseedCollectionGets() || !processingArguments.isReuseSuccessfulResources() ||
                filterArguments.isDryRun() || reportingArguments.isTui() || !filterArguments.isHttpMethodSupplied(HttpMethod.GET)) {
            return;
        }
        int seeded = 0;
        for (Map.Entry<String, PathItem> entry : paths) {
            if (seeded >= processingArguments.getMaxPreseedCollectionGets()) {
                return;
            }
            if (preseedPathIfEligible(entry.getKey(), entry.getValue(), suppliedPaths, openAPI)) {
                seeded++;
            }
        }
    }

    private boolean preseedPathIfEligible(String path, PathItem item, List<String> suppliedPaths, OpenAPI openAPI) {
        if (!isEligibleForPreseed(path, item, suppliedPaths)) {
            return false;
        }
        List<Fuzzer> configured = filterArguments.filterOutFuzzersNotMatchingHttpMethodsAndPath(Set.of(HttpMethod.GET), path);
        Fuzzer happyPath = configured.stream().filter(HappyPathFuzzer.class::isInstance).findFirst().orElse(null);
        if (happyPath == null) {
            return false;
        }
        List<FuzzingData> getData = fuzzingDataFactory.fromPathItem(path, item, openAPI).stream()
                .filter(data -> data.getMethod() == HttpMethod.GET)
                .filter(data -> processingArguments.matchesXxxSelection(data.getPayload()))
                .limit(1).toList();
        if (getData.isEmpty()) {
            return false;
        }
        runFuzzers(getData, List.of(happyPath));
        return true;
    }

    /** Limits discovery to selected GETs that can run without an ID or a required query parameter. */
    private boolean isEligibleForPreseed(String path, PathItem item, List<String> suppliedPaths) {
        return suppliedPaths.contains(path) && !path.contains("{") && item.getGet() != null &&
                !hasRequiredQueryParameters(item) && !isBinaryStreamOnlyResponse(item.getGet());
    }

    private boolean isBinaryStreamOnlyResponse(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getResponses() == null) {
            return false;
        }
        List<String> twoXxMediaTypes = operation.getResponses().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("2") &&
                        entry.getValue() != null && entry.getValue().getContent() != null)
                .flatMap(entry -> entry.getValue().getContent().keySet().stream())
                .toList();
        return !twoXxMediaTypes.isEmpty() && twoXxMediaTypes.stream()
                .allMatch(mt -> mt.contains("octet-stream") || mt.startsWith("image/") ||
                        mt.startsWith("video/") || mt.startsWith("audio/"));
    }

    private boolean hasRequiredQueryParameters(PathItem item) {
        return java.util.stream.Stream.concat(
                        Optional.ofNullable(item.getParameters()).orElse(List.of()).stream(),
                        Optional.ofNullable(item.getGet().getParameters()).orElse(List.of()).stream())
                .anyMatch(parameter -> "query".equals(parameter.getIn()) && Boolean.TRUE.equals(parameter.getRequired()));
    }

    /**
     * Runs the happy path DELETE requests postponed by {@link #runFirstPhaseFuzzers(List, List)} so that parent
     * resources remain available while their child paths are fuzzed. Paths are processed in reverse order,
     * which runs deeper paths (children) before their parents. Second phase fuzzers triggered by
     * {@link SecondPhaseFuzzer.Trigger#PATH_RESOURCES_DELETED} then run for each such path.
     */
    void runDeferredHappyPathDeletes() {
        List<DeferredHappyPathDelete> deferred = List.copyOf(deferredHappyPathDeletes);
        deferredHappyPathDeletes.clear();
        for (DeferredHappyPathDelete delete : deferred.reversed()) {
            CatsExecutionCancelledException.check();
            runFuzzers(delete.deleteData(), delete.deleteFuzzers());
            runSecondPhaseFuzzers(delete.pathData(), SecondPhaseFuzzer.Trigger.PATH_RESOURCES_DELETED);
        }
    }

    void runSecondPhaseFuzzers(List<FuzzingData> fuzzingData, SecondPhaseFuzzer.Trigger trigger) {
        List<Fuzzer> triggeredFuzzers = filterArguments.getSecondPhaseFuzzers().stream()
                .filter(fuzzer -> triggers(fuzzer).contains(trigger))
                .toList();
        runFuzzers(fuzzingData, triggeredFuzzers);
    }

    private static List<SecondPhaseFuzzer.Trigger> triggers(Fuzzer fuzzer) {
        SecondPhaseFuzzer secondPhaseFuzzer = AnnotationUtils.findAnnotation(fuzzer.getClass(), SecondPhaseFuzzer.class);
        return secondPhaseFuzzer == null ? List.of(SecondPhaseFuzzer.Trigger.PATH_COMPLETED) : List.of(secondPhaseFuzzer.triggers());
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
        CatsExecutionCancelledException.check();
        if (filterArguments.getSuppliedFuzzers().contains(FunctionalFuzzer.class.getSimpleName())) {
            functionalFuzzer.executeCustomFuzzerTests();
            CatsExecutionCancelledException.check();
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
        runtimeResourcePool.clear();
        ConsoleUtils.initTerminalWidth(spec);
        reportingArguments.processLogData();
        authArguments.getEnvironmentVariables();
        apiArguments.validateRequired(spec);
        processingArguments.validatePreseedCollectionGets(spec);
        filesArguments.loadConfig();
        filterArguments.applyProfile(spec);
    }

    private void printConfiguration(OpenAPI openAPI) {
        logger.config("OpenAPI specs: {}", AnsiUtils.blue(apiArguments.getContract()));
        logger.config("API base url: {}", AnsiUtils.blue(apiArguments.getServer()));
        logger.config("Call timeout: {} seconds", AnsiUtils.blue(apiArguments.getCallTimeout()));
        logger.config("Maximum captured response body: {} bytes", AnsiUtils.blue(apiArguments.getMaxResponseBytes()));
        logger.config("Reporting path: {}", AnsiUtils.blue(reportingArguments.getOutputReportFolder()));
        logger.config("--insecure: {}", AnsiUtils.blue(authArguments.isInsecure()));
        logger.config("--showSecrets: {}", AnsiUtils.blue(reportingArguments.isShowSecrets()));
        logger.config("Environment file: {}", AnsiUtils.blue(authArguments.getEnvironmentFileStatus()));
        if (wfcAuthProvider.isEnabled()) {
            logger.config("WFC Auth file: {}", AnsiUtils.blue(authArguments.getWfcAuthFile()));
            logger.config("WFC Auth entry: {}", AnsiUtils.blue(wfcAuthProvider.getSelectedAuthenticationName()));
        }
        logger.config("{} configured fuzzers out of {} total fuzzers",
                AnsiUtils.blue(filterArguments.getFirstPhaseFuzzersForPath().size()),
                AnsiUtils.blue(filterArguments.getTotalFuzzersOrLinters()));
        List<String> secondPhaseNames = filterArguments.getSecondPhaseFuzzers().stream().map(Object::toString).toList();
        logger.config("{} second-phase fuzzers configured: {}", AnsiUtils.blue(secondPhaseNames.size()), AnsiUtils.blue(secondPhaseNames));
        if (!secondPhaseNames.isEmpty()) {
            logger.config("Second-phase checks only send requests when matching successful exchanges were observed; use --verbosity=detailed for skip reasons");
            if (!processingArguments.isReuseSuccessfulResources()) {
                logger.config("Second-phase checks requiring observed exchanges cannot run with --no-reuseSuccessfulResources");
            } else if (!filterArguments.getFirstPhaseFuzzersForPath().contains(HappyPathFuzzer.class.getSimpleName())) {
                logger.config("HappyPathFuzzer is not selected; phase-2 checks may have no successful POST/GET/PUT baselines to use");
            }
        }
        logger.config("{} configured paths out of {} total OpenAPI paths",
                AnsiUtils.blue(filterArguments.getPathsToRun(openAPI).size()),
                AnsiUtils.blue(openAPI.getPaths().size()));
        logger.config("HTTP methods in scope: {}", AnsiUtils.blue(filterArguments.getHttpMethods()));
        logger.config("Example flags: useRequestBodyExamples {}, useSchemaExamples {}, usePropertyExamples {}, useResponseBodyExamples {}, useDefaults {}",
                AnsiUtils.blue(processingArguments.isUseRequestBodyExamples()),
                AnsiUtils.blue(processingArguments.isUseSchemaExamples()),
                AnsiUtils.blue(processingArguments.isUsePropertyExamples()),
                AnsiUtils.blue(processingArguments.isUseResponseBodyExamples()),
                AnsiUtils.blue(processingArguments.isUseDefaults()));
        logger.config("selfReferenceDepth {}, largeStringsSize {}, randomHeadersNumber {}, limitFuzzedFields {}, limitXxxOfCombinations {}",
                AnsiUtils.blue(processingArguments.getSelfReferenceDepth()),
                AnsiUtils.blue(processingArguments.getLargeStringsSize()),
                AnsiUtils.blue(processingArguments.getRandomHeadersNumber()),
                AnsiUtils.blue(processingArguments.getLimitNumberOfFields()),
                AnsiUtils.blue(processingArguments.getLimitXxxOfCombinations()));
        logger.config("How the service handles whitespaces and random unicodes: edgeSpacesStrategy {}, sanitizationStrategy {}",
                AnsiUtils.blue(processingArguments.getEdgeSpacesStrategy()),
                AnsiUtils.blue(processingArguments.getSanitizationStrategy()));
        logger.config("Seed value: {}",
                AnsiUtils.blue(CatsRandom.getStoredSeed()));
        logger.config("Quality gate: {}",
                AnsiUtils.blue(qualityGateArguments.getQualityGateDescription()));
        logger.config("Reuse successful runtime resources: {}",
                AnsiUtils.blue(processingArguments.isReuseSuccessfulResources()));

        int nofOfOperations = OpenApiUtils.getNumberOfOperations(openAPI);
        logger.config("Total number of OpenAPI operations: {}", AnsiUtils.blue(nofOfOperations));
    }

    private void fuzzPath(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI) {
        executionEventPublisher.publish(new CatsExecutionEvent.PathStarted(Instant.now(), pathItemEntry.getKey()));
        fuzzPathInternal(pathItemEntry, openAPI);
        executionEventPublisher.publish(new CatsExecutionEvent.PathCompleted(Instant.now(), pathItemEntry.getKey()));
    }

    private void fuzzPathInternal(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI) {
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
        this.runFirstPhaseFuzzers(filteredFuzzingData, fuzzersToRun);
        this.runSecondPhaseFuzzers(filteredFuzzingData, SecondPhaseFuzzer.Trigger.PATH_COMPLETED);
    }

    void runFirstPhaseFuzzers(List<FuzzingData> fuzzingData, List<Fuzzer> configuredFuzzers) {
        if (!processingArguments.isReuseSuccessfulResources() ||
                configuredFuzzers.stream().noneMatch(HappyPathFuzzer.class::isInstance)) {
            runFuzzers(fuzzingData, configuredFuzzers);
            return;
        }
        runSeedFuzzersForPath(fuzzingData, configuredFuzzers);
        runFuzzers(fuzzingData, nonSeedFuzzers(configuredFuzzers));
    }

    private void runSeedFuzzersForPath(List<FuzzingData> fuzzingData, List<Fuzzer> configuredFuzzers) {
        Fuzzer happyPathFuzzer = configuredFuzzers.stream()
                .filter(HappyPathFuzzer.class::isInstance)
                .findFirst()
                .orElse(null);
        if (happyPathFuzzer == null) {
            return;
        }
        Fuzzer onlyRequiredFieldsFuzzer = configuredFuzzers.stream()
                .filter(OnlyRequiredFieldsFuzzer.class::isInstance)
                .findFirst()
                .orElse(null);
        List<Fuzzer> seedFuzzers = onlyRequiredFieldsFuzzer != null
                ? List.of(happyPathFuzzer, onlyRequiredFieldsFuzzer)
                : List.of(happyPathFuzzer);
        List<FuzzingData> seedData = fuzzingData.stream()
                .filter(this::isSafeResourceSeed)
                .sorted(Comparator.comparingInt(data -> seedMethodOrder(data.getMethod())))
                .toList();
        List<FuzzingData> deleteData = fuzzingData.stream().filter(data -> data.getMethod() == HttpMethod.DELETE).toList();
        List<FuzzingData> deferredHappyPathData = fuzzingData.stream()
                .filter(data -> !seedData.contains(data) && !deleteData.contains(data)).toList();
        runFuzzers(seedData, seedFuzzers);
        runFuzzers(deferredHappyPathData, List.of(happyPathFuzzer));
        if (!deleteData.isEmpty()) {
            deferredHappyPathDeletes.add(new DeferredHappyPathDelete(fuzzingData, deleteData, seedFuzzers));
        }
    }

    private boolean isSafeResourceSeed(FuzzingData data) {
        return data.getMethod() == HttpMethod.POST || data.getMethod() == HttpMethod.PUT ||
                data.getMethod() == HttpMethod.PATCH || data.getMethod() == HttpMethod.GET ||
                data.getMethod() == HttpMethod.HEAD;
    }

    private static int seedMethodOrder(HttpMethod method) {
        return switch (method) {
            case POST, GET -> 0;
            case PUT -> 1;
            case PATCH -> 2;
            case HEAD -> 3;
            default -> 4;
        };
    }

    private void runFuzzers(List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered, List<Fuzzer> configuredFuzzers) {
        /*We only run the fuzzers supplied and exclude those that do not apply for certain HTTP methods*/

        for (Fuzzer fuzzer : configuredFuzzers) {
            CatsExecutionCancelledException.check();
            List<FuzzingData> filteredData = this.filterFuzzingData(fuzzingDataListWithHttpMethodsFiltered, fuzzer);
            for (FuzzingData data : filteredData) {
                CatsExecutionCancelledException.check();
                runSingleFuzzer(fuzzer, data);
            }
        }
    }

    private void runSingleFuzzer(Fuzzer fuzzer, FuzzingData data) {
        runSingleFuzzer(fuzzer, data, fuzzer::fuzz);
    }

    private void runSingleFuzzer(Fuzzer fuzzer, FuzzingData data, Consumer<FuzzingData> action) {
        CatsExecutionCancelledException.check();
        if (data.shouldSkipFuzzerForPath(fuzzer.toString())) {
            logger.skip("Skipping Fuzzer {} for path {} due to OpenAPI extension configuration",
                    AnsiUtils.yellow(fuzzer.toString()), data.getPath());
            return;
        }

        logFuzzerStart(fuzzer, data);

        boolean reportsFuzzerLifecycle = !(fuzzer instanceof FunctionalFuzzer);
        if (reportsFuzzerLifecycle) {
            testCaseListener.beforeFuzz(fuzzer.getClass(), data.getContractPath(), data.getMethod().name());
        }

        try {
            action.accept(data);
        } finally {
            if (reportsFuzzerLifecycle) {
                testCaseListener.afterFuzz(data.getContractPath());
            }
        }

        CatsExecutionCancelledException.check();
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
        List<FuzzingData> methodCompatible = CatsUtil.filterAndPrintNotMatching(
                fuzzingDataListWithHttpMethodsFiltered,
                data -> !fuzzer.skipForHttpMethods().contains(data.getMethod()),
                logger,
                "HTTP method {} is not supported by {}",
                t -> t.getMethod().toString(), fuzzer.toString());
        return CatsUtil.filterAndPrintNotMatching(
                methodCompatible,
                fuzzer::isApplicableTo,
                logger,
                "Payload format {} is not supported by {}",
                data -> data.getPayloadFormat().name(), fuzzer.toString());
    }

    @Override
    public int getExitCode() {
        if (exitCodeDueToErrors > 0) {
            return exitCodeDueToErrors;
        }

        long errors = executionStatisticsListener.getErrors();
        long warnings = executionStatisticsListener.getWarns();

        if (qualityGateArguments.shouldFailBuild(errors, warnings)) {
            logger.debug("Build failed due to quality gate: {}", qualityGateArguments.getQualityGateDescription());
            return CommandLine.ExitCode.SOFTWARE;
        }

        return CommandLine.ExitCode.OK;
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
