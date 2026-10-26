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
import com.endava.cats.fuzzer.special.FunctionalFlowFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFlowParser;
import com.endava.cats.fuzzer.special.FunctionalFuzzer;
import com.endava.cats.fuzzer.special.RandomFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsConfiguration;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.openapi.handler.index.SpecPositionIndex;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.UnresolvedParametersSummary;
import com.endava.cats.tui.CatsTuiLauncher;
import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.event.CatsExecutionEventPublisher;
import com.endava.cats.tui.model.RunConfigurationSnapshot;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.ConsoleUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
                "@|bold cats (list | replay | run | template | lint | info | stats | validate | random | generate | explain | smoke | compare | init | analyze | triage)|@ [OPTIONS]", "%n"},
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
                ExplainCommand.class,
                SmokeCommand.class,
                CompareCommand.class,
                InitCommand.class,
                AnalyzeCommand.class,
                TriageCommand.class
        })
public class CatsCommand implements Runnable, CommandLine.IExitCodeGenerator, AutoCloseable {

    static final int CANCELLED_EXIT_CODE = 130;
    private final PrettyLogger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    FuzzingDataFactory fuzzingDataFactory;
    @Inject
    FunctionalFuzzer functionalFuzzer;

    @Inject
    FunctionalFlowFuzzer functionalFlowFuzzer;
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

    private int exitCodeDueToErrors = CommandLine.ExitCode.OK;
    private final FuzzerWaveExecutor waveExecutor;

    /**
     * Creates a new instance of CatsCommand.
     */
    public CatsCommand() {
        logger = PrettyLoggerFactory.getLogger(CatsCommand.class);
        waveExecutor = new FuzzerWaveExecutor(this, logger);
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
        context.getFunctionalFlowFile().ifPresent(filesArguments::setFunctionalFlowFile);
        context.getSecurityFuzzerFile().ifPresent(filesArguments::setSecurityFuzzerFile);
        context.getHeadersFile().ifPresent(filesArguments::setHeadersFile);
        context.getHeadersMap().ifPresent(filesArguments::setHeadersMap);
        context.getCreateRefData().ifPresent(filesArguments::setCreateRefData);
        context.getRefDataFile().ifPresent(filesArguments::setRefDataFile);
        context.getCorrelationsFile().ifPresent(filesArguments::setCorrelationsFile);
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
        context.getFilesArguments().ifPresent(filesArguments::copyFrom);
        context.getSkippedHttpMethods().ifPresent(filterArguments::setSkippedHttpMethods);
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
        waveExecutor.startFuzzing(openAPI);
    }

    void runDeferredHappyPathDeletes() {
        waveExecutor.runDeferredHappyPathDeletes();
    }

    void runSecondPhaseFuzzers(List<FuzzingData> fuzzingData, SecondPhaseFuzzer.Trigger trigger) {
        waveExecutor.runSecondPhaseFuzzers(fuzzingData, trigger);
    }

    private void executeCustomFuzzer() throws IOException {
        CatsExecutionCancelledException.check();
        if (filterArguments.getSuppliedFuzzers().contains(FunctionalFlowFuzzer.class.getSimpleName())
                || filesArguments.getFunctionalFlowFile() != null) {
            functionalFlowFuzzer.executeFlows();
            CatsExecutionCancelledException.check();
        }
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
        if (filesArguments.getCustomFuzzerFile() != null
                && FunctionalFlowParser.isFunctionalFlowFile(filesArguments.getCustomFuzzerFile())) {
            filesArguments.setFunctionalFlowFile(filesArguments.getCustomFuzzerFile());
            filesArguments.setCustomFuzzerFile(null);
            filterArguments.customFilter(FunctionalFlowFuzzer.class.getSimpleName());
        }
        if (filesArguments.getFunctionalFlowFile() != null) {
            logger.config(AnsiUtils.bold("FunctionalFlow file: {}"),
                    AnsiUtils.blue(filesArguments.getFunctionalFlowFile().getCanonicalPath()));
        }
        if (filesArguments.getCorrelationsFile() != null) {
            logger.config(AnsiUtils.bold("Correlations file: {}"),
                    AnsiUtils.blue(filesArguments.getCorrelationsFile().getCanonicalPath()));
        }
        filesArguments.loadConfig();
        if (filesArguments.getCustomFuzzerFile() != null) {
            logger.warning("The legacy path-grouped FunctionalFuzzer YAML format is deprecated and will be removed in a future release. Please migrate to the sequential 'steps:' FunctionalFlow format.");
        }
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
        String activeProfile = processingArguments.isHealthCheck() ? "health-check" : filterArguments.getProfile();
        logger.config("{} configured fuzzers out of {} total fuzzers (profile: {})",
                AnsiUtils.blue(filterArguments.getFirstPhaseFuzzersForPath().size() + filterArguments.getSecondPhaseFuzzers().size()),
                AnsiUtils.blue(filterArguments.getTotalFuzzersOrLinters()),
                AnsiUtils.blue(activeProfile));
        logger.config("{} configured paths out of {} total OpenAPI paths",
                AnsiUtils.blue(filterArguments.getPathsToRun(openAPI).size()),
                AnsiUtils.blue(openAPI.getPaths().size()));
        logger.config("HTTP methods in scope: {}", AnsiUtils.blue(filterArguments.getHttpMethods()));
        if (stopArguments != null && stopArguments.isAnyStopConditionProvided()) {
            logger.config("Stop conditions: time={}s (fillTimeBudget={}), maxErrors={}, maxTests={}",
                    AnsiUtils.blue(stopArguments.getStopAfterTimeInSec()),
                    AnsiUtils.blue(stopArguments.isFillTimeBudget()),
                    AnsiUtils.blue(stopArguments.getStopAfterErrors()),
                    AnsiUtils.blue(stopArguments.getStopAfterMutations()));
        }
        if (ignoreArguments != null && (!ignoreArguments.getIgnoreResponseCodes().isEmpty() ||
                ignoreArguments.isSkipReportingForIgnoredCodes() ||
                ignoreArguments.isSkipReportingForSuccess() ||
                ignoreArguments.isSkipReportingForWarnings())) {
            logger.config("Ignored response codes: {}, skipReportingForIgnored: {}, skipReportingForSuccess: {}, skipReportingForWarnings: {}",
                    AnsiUtils.blue(ignoreArguments.getIgnoreResponseCodes()),
                    AnsiUtils.blue(ignoreArguments.isSkipReportingForIgnoredCodes()),
                    AnsiUtils.blue(ignoreArguments.isSkipReportingForSuccess()),
                    AnsiUtils.blue(ignoreArguments.isSkipReportingForWarnings()));
        }
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

    void runFirstPhaseFuzzers(List<FuzzingData> fuzzingData, List<Fuzzer> configuredFuzzers) {
        waveExecutor.runFirstPhaseFuzzers(fuzzingData, configuredFuzzers);
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
    public void close() {
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
