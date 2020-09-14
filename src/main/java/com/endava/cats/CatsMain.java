package com.endava.cats;

import ch.qos.logback.classic.Level;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.fields.CustomFuzzer;
import com.endava.cats.model.CatsSkipped;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.factory.FuzzingDataFactory;
import com.endava.cats.util.CatsUtil;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.fusesource.jansi.Ansi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * We need to print the build version and build time
 */
@ConditionalOnResource(
        resources = {"${spring.info.build.location:classpath:META-INF/build-info.properties}"}
)
@SpringBootApplication
public class CatsMain implements CommandLineRunner {

    public static final AtomicInteger TEST = new AtomicInteger(0);
    public static final String EMPTY = "empty";
    public static final String ALL = "all";
    private static final String LIST = "list";
    private static final Logger LOGGER = LoggerFactory.getLogger(CatsMain.class);
    private static final String PATHS_STRING = "paths";
    private static final String FUZZERS_STRING = "fuzzers";
    private static final String FIELDS_FUZZER_STRATEGIES = "fieldsFuzzerStrategies";
    private static final String LIST_ALL_COMMANDS = "commands";
    private static final String HELP = "help";
    private static final String VERSION = "version";
    private static final String APPLICATION_JSON = "application/json";
    private static final String EXAMPLE = ansi().fg(Ansi.Color.CYAN).a("./cats.jar --server=http://localhost:8080 --contract=con.yml").reset().toString();
    private static final String COMMAND_TEMPLATE = ansi().render("\t --@|cyan {}|@={}").reset().toString();
    protected List<CatsSkipped> skipFuzzersForPaths;
    @Value("${contract:empty}")
    private String contract;
    @Value("${server:empty}")
    private String server;
    @Value("${fuzzers:all}")
    private String suppliedFuzzers;
    @Value("${log:empty}")
    private String logData;
    @Value("${fieldsFuzzingStrategy:ONEBYONE}")
    private String fieldsFuzzingStrategy;
    @Value("${maxFieldsToRemove:empty}")
    private String maxFieldsToRemove;
    @Value("${paths:all}")
    private String paths;
    @Value("${refData:empty}")
    private String refDataFile;
    @Value("${headers:empty}")
    private String headersFile;
    @Value("${reportingLevel:info}")
    private String reportingLevel;
    @Value("${edgeSpacesStrategy:trimAndValidate}")
    private String edgeSpacesStrategy;
    @Value("${urlParams:empty}")
    private String urlParams;
    @Value("${customFuzzerFile:empty}")
    private String customFuzzerFile;
    @Autowired
    private List<Fuzzer> fuzzers;
    @Autowired
    private FuzzingDataFactory fuzzingDataFactory;
    @Autowired
    private CustomFuzzer customFuzzer;


    public static void main(String... args) {
        new SpringApplicationBuilder(CatsMain.class).bannerMode(Banner.Mode.CONSOLE).logStartupInfo(false).build().run(args);

    }

    private static List<String> stringToList(String str, String splitChar) {
        return Stream.of(str.split(splitChar)).collect(Collectors.toList());
    }

    public static Map<String, Schema> getSchemas(OpenAPI openAPI) {
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();

        Optional.ofNullable(openAPI.getComponents().getRequestBodies())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent()));

        Optional.ofNullable(openAPI.getComponents().getResponses())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent()));

        return schemas;
    }

    private static void addToSchemas(Map<String, Schema> schemas, String schemaName, String ref, Content content) {
        Schema schemaToAdd;
        if (ref == null) {
            Schema refSchema = content.get(APPLICATION_JSON).getSchema();
            if (refSchema instanceof ArraySchema) {
                ref = ((ArraySchema) refSchema).getItems().get$ref();
                refSchema.set$ref(ref);
                schemaToAdd = refSchema;
            } else {
                ref = refSchema.get$ref();
                String schemaKey = ref.substring(ref.lastIndexOf('/') + 1);
                schemaToAdd = schemas.get(schemaKey);
            }
            schemas.put(schemaName, schemaToAdd);
        }
    }

    @Override
    public void run(String... args) {
        try {
            this.doLogic(args);
        } catch (StopExecutionException e) {
            LOGGER.info(" ");
        }
    }

    public void doLogic(String... args) {
        this.sortFuzzersByName();
        this.processArgs(args);
        this.printArgs();
        this.processSkipFuzzerFor(args);

        OpenAPI openAPI = this.createOpenAPI();
        this.processContractDependentCommands(openAPI, args);

        List<String> suppliedPaths = this.matchSuppliedPathsWithContractPaths(openAPI);

        this.startFuzzing(openAPI, suppliedPaths);
        this.executeCustomFuzzer();
    }

    public void processSkipFuzzerFor(String... args) {
        List<String> skipForArgs = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--skip") && arg.contains("ForPath")).collect(Collectors.toList());

        List<CatsSkipped> catsSkipped = skipForArgs.stream()
                .map(skipFor -> skipFor.replace("--skip", "")
                        .replace("ForPath", "").split("="))
                .map(skipForArr -> CatsSkipped.builder()
                        .fuzzer(skipForArr[0].trim())
                        .forPaths(Arrays.asList(skipForArr[1].trim().split(",")))
                        .build())
                .collect(Collectors.toList());
        LOGGER.info("skipXXXForPath supplied arguments: {}. Matching with registered fuzzers...", catsSkipped);

        this.skipFuzzersForPaths = catsSkipped.stream()
                .filter(skipped -> fuzzers.stream().map(Object::toString).anyMatch(fuzzerName -> fuzzerName.equalsIgnoreCase(skipped.getFuzzer())))
                .collect(Collectors.toList());
        LOGGER.info("skipXXXForPath list after matching with registered fuzzers: {}", this.skipFuzzersForPaths);
    }

    public void startFuzzing(OpenAPI openAPI, List<String> suppliedPaths) {
        for (Map.Entry<String, PathItem> entry : this.sortPathsAlphabetically(openAPI)) {

            if (suppliedPaths.contains(entry.getKey())) {
                this.fuzzPath(entry, openAPI);
            } else {
                LOGGER.info("Skipping path {}", entry.getKey());
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
        List<String> suppliedPaths = stringToList(paths, ";");
        if (suppliedPaths.isEmpty() || paths.equalsIgnoreCase(ALL)) {
            suppliedPaths.remove(ALL);
            suppliedPaths.addAll(openAPI.getPaths().keySet());
        }
        suppliedPaths = CatsUtil.filterAndPrintNotMatching(suppliedPaths, path -> openAPI.getPaths().containsKey(path), LOGGER, "Supplied path is not matching the contract {}", Object::toString);

        return suppliedPaths;
    }

    public OpenAPI createOpenAPI() {
        try {
            long t0 = System.currentTimeMillis();
            OpenAPIParser openAPIV3Parser = new OpenAPIParser();
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            options.setFlatten(true);
            OpenAPI openAPI = openAPIV3Parser.readContents(new String(Files.readAllBytes(Paths.get(contract))), null, options).getOpenAPI();

            String finishMessage = ansi().fgGreen().a("Finished parsing the contract in {} ms").reset().toString();
            LOGGER.info(finishMessage, (System.currentTimeMillis() - t0));
            return openAPI;
        } catch (Exception e) {
            LOGGER.error("Error parsing OPEN API contract {}", contract);
            throw new StopExecutionException();
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
            LOGGER.info("Available paths:");
            openAPI.getPaths().keySet().stream().sorted().map(item -> "\t " + item).forEach(LOGGER::info);
            throw new StopExecutionException("list available paths");
        }
    }

    private boolean isListContractPaths(String[] args) {
        return args.length == 3 && args[0].equalsIgnoreCase(LIST) && args[1].equalsIgnoreCase(PATHS_STRING);
    }

    private void processArgs(String[] args) {
        if (args.length == 1) {
            this.processSingleArgument(args);
        }

        if (args.length == 2) {
            this.processTwoArguments(args);
        }

        this.processRemainingArguments(args);

        this.processLogLevelArgument();

        this.setReportingLevel();
    }

    private void processRemainingArguments(String[] args) {
        if (this.isMinimumArgumentsNotSupplied(args)) {
            LOGGER.error("Missing or invalid required arguments 'contract' or 'server'. Usage: ./cats.jar --server=URL --contract=location. You can run './cats.jar commands' for more options.");
            throw new StopExecutionException("minimum arguments not supplied");
        }
    }

    private boolean isMinimumArgumentsNotSupplied(String[] args) {
        return EMPTY.equalsIgnoreCase(contract) && (args.length != 3 || EMPTY.equalsIgnoreCase(server));
    }

    private void setReportingLevel() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.endava.cats")).setLevel(Level.toLevel(reportingLevel));
    }

    private void processLogLevelArgument() {
        if (!EMPTY.equalsIgnoreCase(logData)) {
            String[] log = logData.split(":");
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(log[0])).setLevel(Level.toLevel(log[1]));
            LOGGER.info("Setting log level to {} for package {}", log[1], log[0]);
        }
    }

    private void processTwoArguments(String[] args) {
        if (this.isListFuzzers(args)) {
            LOGGER.info("CATs has {} registered fuzzers:", fuzzers.size());
            fuzzers.stream().map(fuzzer -> "\t" + ansi().fg(Ansi.Color.GREEN).a(fuzzer.toString()).reset().a(" - " + fuzzer.description()).reset()).forEach(LOGGER::info);
            throw new StopExecutionException("list fuzzers");
        }

        if (this.isListFieldsFuzzingStrategies(args)) {
            LOGGER.info("Registered fieldsFuzzerStrategies: {}", Arrays.asList(FuzzingData.SetFuzzingStrategy.values()));
            throw new StopExecutionException("list fieldsFuzzerStrategies");
        }
    }

    private boolean isListFieldsFuzzingStrategies(String[] args) {
        return args[0].equalsIgnoreCase(LIST) && args[1].equalsIgnoreCase(FIELDS_FUZZER_STRATEGIES);
    }

    private boolean isListFuzzers(String[] args) {
        return args[0].equalsIgnoreCase(LIST) && args[1].equalsIgnoreCase(FUZZERS_STRING);
    }

    private void processSingleArgument(String[] args) {
        if (args[0].equalsIgnoreCase(LIST_ALL_COMMANDS)) {
            this.printCommands();
            throw new StopExecutionException("list all commands");
        }

        if (args[0].equalsIgnoreCase(HELP)) {
            this.printUsage();
            throw new StopExecutionException(HELP);
        }

        if (args[0].equalsIgnoreCase(VERSION)) {
            throw new StopExecutionException(VERSION);
        }
    }

    protected void fuzzPath(Map.Entry<String, PathItem> pathItemEntry, OpenAPI openAPI) {
        List<String> configuredFuzzers = this.configuredFuzzers(pathItemEntry.getKey());
        Map<String, Schema> schemas = getSchemas(openAPI);

        /* WE NEED TO ITERATE THROUGH EACH HTTP OPERATION CORRESPONDING TO THE CURRENT PATH ENTRY*/
        LOGGER.info(" ");
        LOGGER.info("Start fuzzing path {}", pathItemEntry.getKey());
        List<FuzzingData> fuzzingDataList = fuzzingDataFactory.fromPathItem(pathItemEntry.getKey(), pathItemEntry.getValue(), schemas);

        if (fuzzingDataList.isEmpty()) {
            LOGGER.warn("Skipping path {}. HTTP method not supported yet!", pathItemEntry.getKey());
            return;
        }

        /*We only run the fuzzers supplied and exclude those that do not apply for certain HTTP methods*/
        for (Fuzzer fuzzer : fuzzers) {
            if (configuredFuzzers.contains(fuzzer.toString())) {
                CatsUtil.filterAndPrintNotMatching(fuzzingDataList, data -> !fuzzer.skipFor().contains(data.getMethod()),
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

    private List<String> configuredFuzzers(String pathKey) {
        List<String> allFuzzersName = fuzzers.stream().map(Object::toString).collect(Collectors.toList());
        List<String> allowedFuzzers = allFuzzersName;

        if (!ALL.equalsIgnoreCase(suppliedFuzzers)) {
            allowedFuzzers = stringToList(suppliedFuzzers, ",");
        }

        allowedFuzzers = this.removeSkippedFuzzers(pathKey, allowedFuzzers);

        return CatsUtil.filterAndPrintNotMatching(allowedFuzzers, allFuzzersName::contains, LOGGER, "Supplied Fuzzer does not exist {}", Object::toString);
    }

    private List<String> removeSkippedFuzzers(String pathKey, List<String> allowedFuzzers) {
        return allowedFuzzers.stream().filter(fuzzer -> skipFuzzersForPaths.stream()
                .noneMatch(catsSkipped -> catsSkipped.getFuzzer().equalsIgnoreCase(fuzzer) && catsSkipped.getForPaths().contains(pathKey)))
                .collect(Collectors.toList());
    }

    private void printUsage() {
        LOGGER.info("The following arguments are supported: ");
        this.renderHelpToConsole("contract", "LOCATION_OF_THE_CONTRACT");
        this.renderHelpToConsole("server", "BASE_URL_OF_THE_SERVICE");
        this.renderHelpToConsole(FUZZERS_STRING, "COMMA_SEPARATED_LIST_OF_FUZZERS the list of fuzzers you want to run. You can use 'all' to include all fuzzers. To list all available fuzzers run: './cats.jar list fuzzers'");
        this.renderHelpToConsole("log", "PACKAGE:LEVEL set custom log level of a given package");
        this.renderHelpToConsole(PATHS_STRING, "PATH_LIST a separated list of paths to test. If no path is supplied, all paths will be considered");
        this.renderHelpToConsole("fieldsFuzzingStrategy", "STRATEGY set the strategy for tge fields fuzzers. Supported strategies ONEBYONE, SIZE, POWERSET");
        this.renderHelpToConsole("maxFieldsToRemove", "NUMBER set the maximum number of fields that will be removed from a request when using the SIZE fieldsFuzzingStrategy");
        this.renderHelpToConsole("refData", "FILE specifies the file with fields that must have a fixed value in order for requests to succeed ");
        this.renderHelpToConsole("headers", "FILE specifies custom headers that will be passed along with request. This can be used to pass oauth or JWT tokens for authentication purposed for example");
        this.renderHelpToConsole("reportingLevel", "LEVEL this can be either INFO, WARN or ERROR. It can be used to suppress INFO logging and focus only on the reporting WARNs and/or ERRORs");
        this.renderHelpToConsole("edgeSpacesStrategy", "STRATEGY this can be either validateAndTrim or trimAndValidate. It can be used to specify what CATS should expect when sending trailing and leading spaces valid values within fields");
        this.renderHelpToConsole("urlParams", "A comma separated list of 'name:value' pairs of parameters to be replaced inside the URLs");
        this.renderHelpToConsole("customFuzzerFile", "A file used by the `CustomFuzzer` that will be used to create user-supplied payloads");
        this.renderHelpToConsole("skipXXXForPath", "/path1,/path2 can configure fuzzers to be excluded for the specified paths");


        LOGGER.info("Example: ");
        LOGGER.info(EXAMPLE);
    }

    private void printCommands() {
        LOGGER.info("Available commands:");
        LOGGER.info("\t./cats.jar help");
        LOGGER.info("\t./cats.jar version");
        LOGGER.info("\t./cats.jar list fuzzers");
        LOGGER.info("\t./cats.jar list fieldsFuzzerStrategies");
        LOGGER.info("\t./cats.jar list paths --contract=CONTRACT");
    }

    private void printArgs() {
        LOGGER.info(" ");
        LOGGER.info("{}", ansi().fgGreen().a("Processing configuration...").reset());
        LOGGER.info("Server: {}", server);
        LOGGER.info("Contract: {}", contract);
        LOGGER.info("{} registered fuzzers: {}", fuzzers.size(), fuzzers);
        LOGGER.info("Supplied fuzzers: {}", suppliedFuzzers);
        LOGGER.info("Fields fuzzing strategy: {}", fieldsFuzzingStrategy);
        LOGGER.info("Max fields to remove: {}", maxFieldsToRemove);
        LOGGER.info("Paths: {}", paths);
        LOGGER.info("Ref data file: {}", refDataFile);
        LOGGER.info("Headers file: {}", headersFile);
        LOGGER.info("Reporting level: {}", reportingLevel);
        LOGGER.info("Edge spaces strategy: {}", edgeSpacesStrategy);
        LOGGER.info("URL parameters: {}", urlParams);
        LOGGER.info("Custom fuzzer file: {}", customFuzzerFile);

    }

    private void renderHelpToConsole(String command, String text) {
        LOGGER.info(COMMAND_TEMPLATE, command, text);
    }
}