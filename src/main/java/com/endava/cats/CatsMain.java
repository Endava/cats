package com.endava.cats;

import ch.qos.logback.classic.Level;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.command.ListCommand;
import com.endava.cats.command.ReplayCommand;
import com.endava.cats.fuzzer.Fuzzer;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.util.MimeTypeUtils;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.spring.boot.autoconfigure.PicocliAutoConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * We need to print the build version and build time
 */
@CommandLine.Command(
        name = "cats",
        mixinStandardHelpOptions = true,
        usageHelpWidth = 100,
        header = "cats - OpenAPI fuzzer and negative testing tool; version 7.0.0%n",
        version = "cats 7.0.0",
        subcommands = {
                AutoComplete.GenerateCompletion.class,
                ListCommand.class,
                ReplayCommand.class
        })
@ComponentScan(basePackages = {"com.endava.cats"})
@Import({PicocliAutoConfiguration.class})
@SpringBootConfiguration
public class CatsMain implements CommandLineRunner, ExitCodeGenerator, Runnable {
    public static final AtomicInteger TEST = new AtomicInteger(0);
    public static final String ALL = "all";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(CatsMain.class);

    /*API Arguments*/
    @Autowired
    @CommandLine.ArgGroup(heading = "API Options:%n", exclusive = false)
    private ApiArguments apiArguments;

    @Autowired
    @CommandLine.ArgGroup(heading = "Authentication Options:%n", exclusive = false)
    private AuthArguments authArgs;

    @Autowired
    @CommandLine.ArgGroup(heading = "Check Options:%n", exclusive = false)
    private CheckArguments checkArgs;

    @Autowired
    @CommandLine.ArgGroup(heading = "Files Options:%n", exclusive = false)
    private FilesArguments filesArguments;

    @Autowired
    @CommandLine.ArgGroup(heading = "Filter Options:%n", exclusive = false)
    private FilterArguments filterArguments;

    @Autowired
    @CommandLine.ArgGroup(heading = "Ignore Options:%n", exclusive = false)
    private IgnoreArguments ignoreArguments;

    @Autowired
    @CommandLine.ArgGroup(heading = "Processing Options:%n", exclusive = false)
    private ProcessingArguments processingArguments;

    @Autowired
    @CommandLine.ArgGroup(heading = "Reporting Options:%n", exclusive = false)
    private ReportingArguments reportingArguments;

    @CommandLine.Unmatched
    private String[] unmatched;

    private int exitCode;

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
    @Autowired
    private CommandLine.IFactory factory;


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

    public void run() {
        try {
            testCaseListener.startSession();
            this.doLogic();
            testCaseListener.endSession();
        } catch (Exception e) {
            CatsUtil.setCatsLogLevel(Level.INFO);
            LOGGER.fatal("Something went wrong while running CATS!", e);
        }
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(this, factory)
                .setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    }

    public void doLogic() throws IOException {
        this.sortFuzzersByName();
        this.processArgs();
        OpenAPI openAPI = this.createOpenAPI();
        this.processFilesAndFilterArguments();
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
        List<String> suppliedPaths = new ArrayList<>(filterArguments.getPaths());
        if (suppliedPaths.isEmpty() || filterArguments.getPaths().isEmpty()) {
            suppliedPaths.addAll(openAPI.getPaths().keySet());
        }
        List<String> skipPaths = filterArguments.getSkipPaths();
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

    private void processArgs() {
        this.processLogLevelArgument();
    }

    private void processFilesAndFilterArguments() throws IOException {
        filesArguments.loadConfig();
        filterArguments.loadConfig(unmatched);
    }


    private void processLogLevelArgument() {
        for (String logLine : reportingArguments.getLogData()) {
            String[] log = logLine.strip().trim().split(":");
            CatsUtil.setLogLevel(log[0], Level.toLevel(log[1]));
            LOGGER.info("Setting log level to {} for package {}", log[1], log[0]);
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
                            testCaseListener.beforeFuzz(fuzzer.getClass());
                            fuzzer.fuzz(data);
                            testCaseListener.afterFuzz();
                        });
            } else {
                LOGGER.debug("Skipping fuzzer {} for path {} as configured!", fuzzer, pathItemEntry.getKey());
            }
        }
    }
    
    @Override
    public int getExitCode() {
        return exitCode;
    }
}