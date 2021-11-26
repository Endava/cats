package com.endava.cats;

import ch.qos.logback.classic.Level;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CatsArg;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.command.ReplayCommand;
import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.fuzzer.SpecialFuzzer;
import com.endava.cats.fuzzer.fields.CustomFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.factory.FuzzingDataFactory;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.fusesource.jansi.Ansi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * We need to print the build version and build time
 */
@ConditionalOnResource(
        resources = {"${spring.info.build.location:classpath:META-INF/build-info.properties}"}
)
@Import({AopAutoConfiguration.class, GsonAutoConfiguration.class, ProjectInfoAutoConfiguration.class})
@ComponentScan(basePackages = {"com.endava.cats"})
@SpringBootConfiguration
public class CatsMain implements CommandLineRunner, ExitCodeGenerator {
    public static final AtomicInteger TEST = new AtomicInteger(0);
    public static final String ALL = "all";
    private static final String LIST = "list";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(CatsMain.class);
    private static final String PATHS_STRING = "paths";
    private static final String FUZZERS_STRING = "fuzzers";
    private static final String FIELDS_FUZZER_STRATEGIES = "fieldsFuzzerStrategies";
    private static final String HELP = "help";
    private static final String VERSION = "version";
    private static final String EXAMPLE = ansi().fg(Ansi.Color.CYAN).a("./cats.jar --server=http://localhost:8080 --contract=con.yml").reset().toString();
    private static final String COMMAND_TEMPLATE = ansi().render("\t --@|cyan {}|@={}").reset().toString();

    @Autowired
    private ApiArguments apiArguments;
    @Autowired
    private ProcessingArguments processingArguments;
    @Autowired
    private FilterArguments filterArguments;
    @Autowired
    private FilesArguments filesArguments;
    @Autowired
    private ReportingArguments reportingArguments;
    @Autowired
    private CheckArguments checkArgs;
    @Autowired
    private AuthArguments authArgs;
    @Autowired
    private ReplayCommand replayCommand;
    @Autowired
    private List<Fuzzer> fuzzers;
    @Autowired
    private FuzzingDataFactory fuzzingDataFactory;
    @Autowired
    private CustomFuzzer customFuzzer;
    @Autowired
    private ExecutionStatisticsListener executionStatisticsListener;
    @Autowired
    private TestCaseListener testCaseListener;

    public static void main(String... args) {
        System.exit(SpringApplication.exit(new SpringApplicationBuilder(CatsMain.class).bannerMode(Banner.Mode.CONSOLE).logStartupInfo(false).build().run(args)));
    }

    public static Map<String, Schema> getSchemas(OpenAPI openAPI) {
        Map<String, Schema> schemas = Optional.ofNullable(openAPI.getComponents().getSchemas())
                .orElseGet(HashMap::new);

        Optional.ofNullable(openAPI.getComponents().getRequestBodies())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent()));

        Optional.ofNullable(openAPI.getComponents().getResponses())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent()));

        return schemas;
    }

    private static void addToSchemas(Map<String, Schema> schemas, String schemaName, String ref, Content content) {
        Schema<?> schemaToAdd = new Schema();
        if (ref == null && isJsonContentType(content)) {
            Schema<?> refSchema = content.get(MimeTypeUtils.APPLICATION_JSON_VALUE).getSchema();

            if (refSchema instanceof ArraySchema) {
                ref = ((ArraySchema) refSchema).getItems().get$ref();
                refSchema.set$ref(ref);
                schemaToAdd = refSchema;
            } else if (refSchema.get$ref() != null) {
                ref = refSchema.get$ref();
                String schemaKey = ref.substring(ref.lastIndexOf('/') + 1);
                schemaToAdd = schemas.get(schemaKey);
            }
        } else if (content != null) {
            LOGGER.warn("CATS only supports application/json as content-type. Found: {} for {}", content.keySet(), schemaName);
        }
        schemas.put(schemaName, schemaToAdd);
    }

    private static boolean isJsonContentType(Content content) {
        return content != null && content.get(MimeTypeUtils.APPLICATION_JSON_VALUE) != null;
    }

    @Override
    public void run(String... args) {
        try {
            testCaseListener.startSession();
            this.doLogic(args);
            testCaseListener.endSession();
        } catch (StopExecutionException e) {
            LOGGER.debug("StopExecution: {}", e.getMessage());
        } catch (Exception e) {
            CatsUtil.setCatsLogLevel(Level.INFO);
            LOGGER.fatal("Something went wrong while running CATS!", e);
        }
    }

    public void doLogic(String... args) throws IOException {
        this.sortFuzzersByName();
        this.processArgs(args);
        OpenAPI openAPI = this.createOpenAPI();
        this.processContractDependentCommands(openAPI, args);
        this.processFilesAndFilterArguments(args);
        this.printArgs();
        List<String> suppliedPaths = this.matchSuppliedPathsWithContractPaths(openAPI);

        testCaseListener.initReportingPath();
        this.startFuzzing(openAPI, suppliedPaths);
        this.executeCustomFuzzer();
    }


    public void startFuzzing(OpenAPI openAPI, List<String> suppliedPaths) {
        Map<String, Schema> schemas = getSchemas(openAPI);
        for (Map.Entry<String, PathItem> entry : this.sortPathsAlphabetically(openAPI)) {

            if (suppliedPaths.contains(entry.getKey())) {
                this.fuzzPath(entry, openAPI, schemas);
            } else {
                LOGGER.skip("Skipping path {}", entry.getKey());
            }
        }
    }

    private LinkedHashSet<Map.Entry<String, PathItem>> sortPathsAlphabetically(OpenAPI openAPI) {
        return openAPI.getPaths().entrySet()
                .stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void executeCustomFuzzer() {
        customFuzzer.executeCustomFuzzerTests();
    }

    private void sortFuzzersByName() {
        fuzzers.sort(Comparator.comparing(fuzzer -> fuzzer.getClass().getSimpleName()));
    }

    /**
     * Check if there are any supplied paths and match them against the contract
     *
     * @param openAPI the OpenAPI object parsed from the contract
     * @return the list of paths from the contract matching the supplied list
     */
    private List<String> matchSuppliedPathsWithContractPaths(OpenAPI openAPI) {
        List<String> suppliedPaths = Stream.of(filterArguments.getPaths().split(",")).collect(Collectors.toList());
        if (suppliedPaths.isEmpty() || filterArguments.getPaths().equalsIgnoreCase(ALL)) {
            suppliedPaths.remove(ALL);
            suppliedPaths.addAll(openAPI.getPaths().keySet());
        }
        List<String> skipPaths = Stream.of(filterArguments.getSkipPaths().split(",")).collect(Collectors.toList());
        suppliedPaths = suppliedPaths.stream().filter(path -> !skipPaths.contains(path)).collect(Collectors.toList());

        suppliedPaths = CatsUtil.filterAndPrintNotMatching(suppliedPaths, path -> openAPI.getPaths().containsKey(path), LOGGER, "Supplied path is not matching the contract {}", Object::toString);

        return suppliedPaths;
    }

    public OpenAPI createOpenAPI() throws IOException {
        String finishMessage = ansi().fgGreen().a("Finished parsing the contract in {} ms").reset().toString();
        long t0 = System.currentTimeMillis();
        OpenAPI openAPI = this.getOpenAPI();
        LOGGER.complete(finishMessage, (System.currentTimeMillis() - t0));
        return openAPI;
    }

    private OpenAPI getOpenAPI() throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);

        if (apiArguments.isRemoteContract()) {
            return openAPIV3Parser.readLocation(apiArguments.getContract(), null, options).getOpenAPI();
        } else {
            return openAPIV3Parser.readContents(Files.readString(Paths.get(apiArguments.getContract())), null, options).getOpenAPI();
        }
    }

    /**
     * Encapsulates logic regarding commands that need to deal with the contract. Like printing the available paths for example.
     *
     * @param openAPI the OpenAPI object parsed from the contract
     * @param args    program arguments
     */
    private void processContractDependentCommands(OpenAPI openAPI, String[] args) {
        if (this.isListContractPaths(args)) {
            LOGGER.star("Available paths:");
            openAPI.getPaths().keySet().stream().sorted().map(item -> "\t " + item).forEach(LOGGER::info);
            throw new StopExecutionException("list available paths");
        }
    }

    private boolean isListContractPaths(String[] args) {
        return args.length == 3 && args[0].equalsIgnoreCase(LIST) && args[1].equalsIgnoreCase(PATHS_STRING);
    }

    private void processArgs(String[] args) {
        if (args.length == 0) {
            this.processNoArgument();
        }
        if (args.length == 1) {
            this.processSingleArgument(args);
        }

        if (args.length == 2) {
            this.processTwoArguments(args);
        }

        this.checkMinimumArguments(args);
        this.processLogLevelArgument();
    }

    private void processFilesAndFilterArguments(String[] args) throws IOException {
        filesArguments.loadConfig();
        filterArguments.loadConfig(args);
    }

    private void checkMinimumArguments(String[] args) {
        if (this.isMinimumArgumentsNotSupplied(args)) {
            LOGGER.error("Missing or invalid required arguments 'contract' or 'server'. Usage: ./cats.jar --server=URL --contract=location. You can run './cats.jar' with no arguments for more options.");
            throw new StopExecutionException("minimum arguments not supplied");
        }
    }

    private boolean isMinimumArgumentsNotSupplied(String[] args) {
        return apiArguments.isContractEmpty() && (args.length != 3 || apiArguments.isContractEmpty());
    }

    private void processLogLevelArgument() {
        if (reportingArguments.hasLogData()) {
            String[] logList = reportingArguments.getLogData().split(",");
            for (String logLine : logList) {
                String[] log = logLine.strip().trim().split(":");
                CatsUtil.setLogLevel(log[0], Level.toLevel(log[1]));
                LOGGER.info("Setting log level to {} for package {}", log[1], log[0]);
            }
        }
    }

    private void processTwoArguments(String[] args) {
        if (this.isListFuzzers(args)) {
            this.listFuzzers();
            throw new StopExecutionException("list fuzzers");
        }

        if (this.isListFieldsFuzzingStrategies(args)) {
            LOGGER.info("Registered fieldsFuzzerStrategies: {}", Arrays.asList(FuzzingData.SetFuzzingStrategy.values()));
            throw new StopExecutionException("list fieldsFuzzerStrategies");
        }
        if (this.isReplay(args)) {
            replayCommand.execute();
            throw new StopExecutionException("replay test");
        }
    }

    private boolean isReplay(String... args) {
        return args[0].equalsIgnoreCase("replay");
    }

    private void listFuzzers() {
        String message = ansi().bold().fg(Ansi.Color.GREEN).a("CATS has {} registered fuzzers:").reset().toString();
        LOGGER.info(message, fuzzers.size());
        filterAndDisplay(FieldFuzzer.class);
        filterAndDisplay(HeaderFuzzer.class);
        filterAndDisplay(HttpFuzzer.class);
        filterAndDisplay(ContractInfoFuzzer.class);
        filterAndDisplay(SpecialFuzzer.class);
    }

    private void filterAndDisplay(Class<? extends Annotation> annotation) {
        List<Fuzzer> fieldFuzzers = fuzzers.stream().filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null).collect(Collectors.toList());
        String message = ansi().bold().fg(Ansi.Color.CYAN).a("{} {} Fuzzers:").reset().toString();
        String typeOfFuzzers = annotation.getSimpleName().replace("Fuzzer", "");
        LOGGER.info(" ");
        LOGGER.info(message, fieldFuzzers.size(), typeOfFuzzers);
        fieldFuzzers.stream().map(fuzzer -> "\t ◼ " + ansi().bold().fg(Ansi.Color.GREEN).a(fuzzer.toString()).reset().a(" - " + fuzzer.description()).reset()).forEach(LOGGER::info);
    }

    private boolean isListFieldsFuzzingStrategies(String[] args) {
        return args[0].equalsIgnoreCase(LIST) && args[1].equalsIgnoreCase(FIELDS_FUZZER_STRATEGIES);
    }

    private boolean isListFuzzers(String[] args) {
        return args[0].equalsIgnoreCase(LIST) && args[1].equalsIgnoreCase(FUZZERS_STRING);
    }

    private void processNoArgument() {
        this.printCommands();
        this.printUsage();
        throw new StopExecutionException("list all commands");
    }

    private void processSingleArgument(String[] args) {
        if (args[0].endsWith(HELP) || args[0].equalsIgnoreCase("-h")) {
            this.printUsage();
            throw new StopExecutionException(HELP);
        }

        if (args[0].endsWith(VERSION) || args[0].equalsIgnoreCase("-v")) {
            throw new StopExecutionException(VERSION);
        }
    }

    public void fuzzPath(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI, Map<String, Schema> schemas) {
        List<String> configuredFuzzers = filterArguments.getFuzzersForPath(pathItemEntry.getKey());
        filterArguments.printWarningIfNeeded(configuredFuzzers);

        /* WE NEED TO ITERATE THROUGH EACH HTTP OPERATION CORRESPONDING TO THE CURRENT PATH ENTRY*/
        LOGGER.info(" ");
        LOGGER.start("Start fuzzing path {}", pathItemEntry.getKey());
        List<FuzzingData> fuzzingDataList = fuzzingDataFactory.fromPathItem(pathItemEntry.getKey(), pathItemEntry.getValue(), schemas, openAPI);

        if (fuzzingDataList.isEmpty()) {
            LOGGER.warning("Skipping path {}. HTTP method not supported yet!", pathItemEntry.getKey());
            return;
        }

        List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered = fuzzingDataList.stream()
                .filter(fuzzingData -> filterArguments.getHttpMethods().contains(fuzzingData.getMethod()))
                .collect(Collectors.toList());
        List<HttpMethod> excludedHttpMethods = fuzzingDataList.stream()
                .map(FuzzingData::getMethod)
                .filter(method -> !filterArguments.getHttpMethods().contains(method))
                .collect(Collectors.toList());

        LOGGER.info("The following HTTP methods won't be executed for path {}: {}", pathItemEntry.getKey(), excludedHttpMethods);
        LOGGER.info("{} configured fuzzers out of {} total fuzzers: {}", configuredFuzzers.size(), fuzzers.size(), configuredFuzzers);

        /*We only run the fuzzers supplied and exclude those that do not apply for certain HTTP methods*/
        for (Fuzzer fuzzer : fuzzers) {
            if (configuredFuzzers.contains(fuzzer.toString())) {
                CatsUtil.filterAndPrintNotMatching(fuzzingDataListWithHttpMethodsFiltered, data -> !fuzzer.skipForHttpMethods().contains(data.getMethod()),
                                LOGGER, "HTTP method {} is not supported by {}", t -> t.getMethod().toString(), fuzzer.toString())
                        .forEach(data -> {
                            LOGGER.info("Fuzzer {} and payload: {}", ansi().fgGreen().a(fuzzer.toString()).reset(), data.getPayload());
                            fuzzer.fuzz(data);
                        });
            } else {
                LOGGER.debug("Skipping fuzzer {} for path {} as configured!", fuzzer, pathItemEntry.getKey());
            }
        }
    }


    private void printUsage() {
        LOGGER.info("The following arguments are supported: ");
        filterArguments.init();
        this.handleArgs(this::renderHelpToConsole);

        LOGGER.note("Example: ");
        LOGGER.note(EXAMPLE);
    }

    private void printCommands() {
        LOGGER.info("Available commands:");
        LOGGER.info("\t./cats.jar help");
        LOGGER.info("\t./cats.jar version");
        LOGGER.info("\t./cats.jar list fuzzers");
        LOGGER.info("\t./cats.jar list fieldsFuzzerStrategies");
        LOGGER.info("\t./cats.jar list paths --contract=CONTRACT");
        LOGGER.info("\t./cats.jar replay --testCases=\"test1,test2\"");
    }

    private void printArgs() {
        LOGGER.info(" ");
        LOGGER.info("Supplied arguments");
        filterArguments.loadConfig();
        this.handleArgs(this::printArg);
    }

    private void handleArgs(Consumer<CatsArg> consumer) {
        apiArguments.getArgs().forEach(consumer);
        filterArguments.getArgs().forEach(consumer);
        checkArgs.getArgs().forEach(consumer);
        processingArguments.getArgs().forEach(consumer);
        reportingArguments.getArgs().forEach(consumer);
        filesArguments.getArgs().forEach(consumer);
        authArgs.getArgs().forEach(consumer);
    }

    private void printArg(CatsArg arg) {
        LOGGER.info("{}: {}", arg.getName(), arg.getValue());
    }

    private void renderHelpToConsole(CatsArg catsArg) {
        LOGGER.info(COMMAND_TEMPLATE, catsArg.getName(), catsArg.getHelp());
    }

    @Override
    public int getExitCode() {
        return executionStatisticsListener.getErrors();
    }
}