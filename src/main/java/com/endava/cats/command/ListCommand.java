package com.endava.cats.command;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.Linter;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.args.util.ProfileLoader;
import com.endava.cats.command.model.FuzzerListEntry;
import com.endava.cats.command.model.MutatorEntry;
import com.endava.cats.command.model.PathDetailsEntry;
import com.endava.cats.command.model.PathListEntry;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutatorConfig;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.AnnotationUtils;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * List various information such as: OpenAPI paths, fuzzers list, format supported by CATS and so on.
 */
@Slf4j
@CommandLine.Command(
        name = "list",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "List Fuzzers, OpenAPI paths, OpenAPI formats, Mutators, Custom Mutator Types and FieldFuzzing strategies",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  List all paths for an OpenAPI contract:",
                "    cats list --paths -c openapi.yml",
                "", "  List all available fuzzers:",
                "    cats list --fuzzers"},
        versionProvider = VersionProvider.class)
@Unremovable
public class ListCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    private final List<Fuzzer> fuzzersList;

    private final List<String> formats;
    private final List<MutatorEntry> mutators;
    private ProfileLoader profileLoader;

    @CommandLine.ArgGroup(multiplicity = "1")
    ListCommandGroups listCommandGroups;
    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output to console in JSON format")
    private boolean json;

    /**
     * Constructs a new instance of the {@code ListCommand} class.
     *
     * @param fuzzersList   an instance containing a list of fuzzers, excluding those annotated with {@code ValidateAndTrim} or {@code ValidateAndSanitize}
     * @param formats       an instance containing a list of OpenAPI formats, including their matching formats
     * @param mutators      an instance containing a list of mutators
     * @param profileLoader an instance of the {@code ProfileLoader} class
     */
    public ListCommand(ProfileLoader profileLoader, @Any Instance<Fuzzer> fuzzersList, @Any Instance<OpenAPIFormat> formats, @Any Instance<Mutator> mutators) {
        this.fuzzersList = fuzzersList.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), ValidateAndTrim.class) == null)
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), ValidateAndSanitize.class) == null)
                .toList();
        this.formats = formats.stream().flatMap(format -> format.matchingFormats().stream()).sorted().toList();
        this.mutators = mutators.stream().map(m -> new MutatorEntry(m.getClass().getSimpleName(), m.description())).sorted().toList();
        this.profileLoader = profileLoader;
    }

    @Override
    public void run() {
        CatsRandom.initRandom(0);
        if (listCommandGroups.listFuzzersGroup != null && listCommandGroups.listFuzzersGroup.fuzzers) {
            listFuzzers();
        }
        if (listCommandGroups.listFuzzersGroup != null && listCommandGroups.listFuzzersGroup.linters) {
            listLinters();
        }
        if (listCommandGroups.listStrategiesGroup != null && listCommandGroups.listStrategiesGroup.strategies) {
            listFuzzerStrategies();
        }
        if (listCommandGroups.listContractOptions != null && listCommandGroups.listContractOptions.paths) {
            listContractPaths();
        }
        if (listCommandGroups.listFormats != null && listCommandGroups.listFormats.formats) {
            listFormats();
        }

        if (listCommandGroups.listMutatorsGroup != null && listCommandGroups.listMutatorsGroup.mutators) {
            listMutators();
        }

        if (listCommandGroups.listMutatorsGroup != null && listCommandGroups.listMutatorsGroup.customMutatorTypes) {
            listMutatorsTypes();
        }

        if (listCommandGroups.listProfilesGroup != null && listCommandGroups.listProfilesGroup.profiles) {
            listProfiles();
        }
    }

    void listProfiles() {
        Collection<ProfileLoader.Profile> profiles = profileLoader.getAvailableProfilesDetails();
        if (json) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(profiles));
        } else {
            logger.noFormat(AnsiUtils.boldGreen("Registered profiles:"));
            profiles.stream()
                    .map(profile -> " ◼ " + AnsiUtils.boldGreen(profile.name()) + " (" + profile.description() + "): " + profile.fuzzers() + System.lineSeparator())
                    .forEach(logger::noFormat);
        }
    }

    void listLinters() {
        List<Fuzzer> linters = filterFuzzers(Linter.class);
        if (json) {
            List<FuzzerListEntry> fuzzerEntries = List.of(new FuzzerListEntry().category("Linters").fuzzers(linters));
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(fuzzerEntries));
        } else {
            String message = AnsiUtils.boldGreen("CATS has {} registered linters:");
            logger.noFormat(message, fuzzersList.size());
            displayFuzzers(linters, Linter.class);
        }
    }

    void listFormats() {
        if (json) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(formats));
        } else {
            logger.noFormat("Registered OpenAPI formats: {}", formats);
        }
    }

    void listMutatorsTypes() {
        if (json) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(CustomMutatorConfig.Type.values()));
        } else {
            String message = AnsiUtils.boldGreen("CATS has {} registered custom mutator types: {}");
            logger.noFormat(message, CustomMutatorConfig.Type.values().length, Arrays.toString(CustomMutatorConfig.Type.values()));
        }
    }

    void listMutators() {
        if (json) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(mutators));
        } else {
            String message = AnsiUtils.boldGreen("CATS has {} registered Mutators:");
            logger.noFormat(message, mutators.size());
            mutators.stream()
                    .map(m -> " ◼ " + AnsiUtils.boldGreen(m.name()) + " - " + m.description())
                    .forEach(logger::noFormat);
        }
    }

    void listContractPaths() {
        try {
            OpenAPI openAPI = OpenApiUtils.readOpenApi(listCommandGroups.listContractOptions.contract);
            if (listCommandGroups.listContractOptions.path == null) {
                this.listAllPaths(openAPI);
            } else {
                this.listPath(openAPI, listCommandGroups.listContractOptions.path);
            }
        } catch (IOException e) {
            logger.debug("Exception while reading contract!", e);
            logger.error("Error while reading contract. The file might not exist or is not reachable: {}. Error message: {}",
                    listCommandGroups.listContractOptions.contract, e.getMessage());
        }
    }

    void listPath(OpenAPI openAPI, String path) {
        PathItem pathItem = openAPI.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(path))
                .map(Map.Entry::getValue)
                .findFirst().orElseThrow();
        List<PathDetailsEntry.OperationDetails> operations = new ArrayList<>();
        for (Map.Entry<PathItem.HttpMethod, Operation> entry : pathItem.readOperationsMap().entrySet()) {
            PathItem.HttpMethod httpMethod = entry.getKey();
            Operation operation = entry.getValue();
            Set<String> responseCodes = OpenApiUtils.responseCodesFromOperations(operation);
            Set<String> headers = OpenApiUtils.headersFromOperation(new String[]{""}, operation);
            Set<String> queryParams = OpenApiUtils.queryParametersFromOperations(operation);

            PathDetailsEntry.OperationDetails operationDetails = PathDetailsEntry.OperationDetails.builder()
                    .operationId(operation.getOperationId())
                    .queryParams(queryParams)
                    .responses(responseCodes)
                    .headers(headers)
                    .httpMethod(httpMethod.name())
                    .build();

            operations.add(operationDetails);
        }
        PathDetailsEntry pathDetailsEntry = PathDetailsEntry.builder()
                .path(path)
                .operations(operations)
                .build();

        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(pathDetailsEntry));
        } else {
            logger.noFormat(AnsiUtils.bold(path + ":"));
            for (PathDetailsEntry.OperationDetails operation : pathDetailsEntry.getOperations()) {
                logger.noFormat(ConsoleUtils.SEPARATOR);
                logger.noFormat(" ◼ Operation: " + operation.getOperationId());
                logger.noFormat(" ◼ HTTP Method: " + operation.getHttpMethod());
                logger.noFormat(" ◼ Response Codes: " + operation.getResponses());
                logger.noFormat(" ◼ HTTP Headers: " + operation.getHeaders());
                logger.noFormat(" ◼ Query Params: " + operation.getQueryParams());
            }
        }
    }

    private void listAllPaths(OpenAPI openAPI) {
        PathListEntry pathListEntry = createPathEntryList(openAPI);

        if (json) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(pathListEntry));
        } else {
            logger.noFormat("{} paths and {} operations:", pathListEntry.getNumberOfPaths(), pathListEntry.getNumberOfOperations());
            pathListEntry.getPathDetailsList()
                    .stream()
                    .sorted()
                    .map(item -> " ◼ " + item.getPath() + ": " + item.getMethods())
                    .forEach(logger::noFormat);
        }
    }

    private PathListEntry createPathEntryList(OpenAPI openAPI) {
        Map<String, PathItem> filteredPaths = Optional.ofNullable(openAPI.getPaths()).orElse(new io.swagger.v3.oas.models.Paths())
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().readOperationsMap().values()
                        .stream()
                        .anyMatch(operation -> listCommandGroups.listContractOptions.tag == null ||
                                Optional.ofNullable(operation.getTags()).orElse(Collections.emptyList())
                                        .contains(listCommandGroups.listContractOptions.tag)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        int numberOfPaths = filteredPaths.size();
        int numberOfOperations = filteredPaths.values().stream().mapToInt(OpenApiUtils::countPathOperations).sum();
        List<PathListEntry.PathDetails> pathDetailsList = new ArrayList<>();

        filteredPaths
                .forEach((pathName, pathItem) -> {
                    List<HttpMethod> httpMethods = HttpMethod.OPERATIONS.entrySet()
                            .stream()
                            .filter(entry -> entry.getValue().apply(pathItem) != null)
                            .map(Map.Entry::getKey)
                            .toList();

                    pathDetailsList.add(PathListEntry.PathDetails
                            .builder()
                            .methods(httpMethods)
                            .path(pathName)
                            .build());
                });
        return PathListEntry.builder()
                .numberOfPaths(numberOfPaths)
                .numberOfOperations(numberOfOperations)
                .pathDetailsList(pathDetailsList)
                .build();
    }

    void listFuzzerStrategies() {
        logger.noFormat("Registered fieldsFuzzerStrategies: {}", Arrays.asList(FuzzingData.SetFuzzingStrategy.values()));
    }

    void listFuzzers() {
        List<Fuzzer> fieldFuzzers = filterFuzzers(FieldFuzzer.class);
        List<Fuzzer> headerFuzzers = filterFuzzers(HeaderFuzzer.class);
        List<Fuzzer> httpFuzzers = filterFuzzers(HttpFuzzer.class);
        List<Fuzzer> contractInfo = filterFuzzers(Linter.class);

        if (json) {
            List<FuzzerListEntry> fuzzerEntries = List.of(
                    new FuzzerListEntry().category("Field").fuzzers(fieldFuzzers),
                    new FuzzerListEntry().category("Header").fuzzers(headerFuzzers),
                    new FuzzerListEntry().category("HTTP").fuzzers(httpFuzzers),
                    new FuzzerListEntry().category("Linters").fuzzers(contractInfo));
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(fuzzerEntries));
        } else {
            String message = AnsiUtils.boldGreen("CATS has {} registered fuzzers:");
            logger.noFormat(message, fuzzersList.size());
            displayFuzzers(fieldFuzzers, FieldFuzzer.class);
            displayFuzzers(headerFuzzers, HeaderFuzzer.class);
            displayFuzzers(httpFuzzers, HttpFuzzer.class);
            displayFuzzers(contractInfo, Linter.class);
        }
    }

    List<Fuzzer> filterFuzzers(Class<? extends Annotation> annotation) {
        return fuzzersList.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null)
                .sorted(Comparator.comparing(Object::toString))
                .toList();
    }

    void displayFuzzers(List<Fuzzer> fuzzers, Class<? extends Annotation> annotation) {
        String message = AnsiUtils.boldColor("{} {}:", Ansi.Color.CYAN);
        String typeOfFuzzers = annotation.getSimpleName().replace("Fuzzer", "");
        logger.noFormat(" ");
        logger.noFormat(message, fuzzers.size(), typeOfFuzzers);
        fuzzers.stream().map(fuzzer -> " ◼ " + AnsiUtils.boldGreen(ConsoleUtils.removeTrimSanitize(fuzzer.toString())) + " - " + fuzzer.description()).forEach(logger::noFormat);
    }

    static class ListCommandGroups {
        @CommandLine.ArgGroup(exclusive = false, heading = "List OpenAPI Contract Paths%n")
        ListContractOptions listContractOptions;

        @CommandLine.ArgGroup(multiplicity = "1", heading = "List Fuzzers%n")
        ListFuzzersGroup listFuzzersGroup;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Mutators%n")
        ListMutatorsGroup listMutatorsGroup;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Fields Fuzzer Strategies%n")
        ListStrategies listStrategiesGroup;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Supported OpenAPI Formats%n")
        ListFormats listFormats;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Profiles%n")
        ListProfilesGroup listProfilesGroup;
    }

    static class ListFormats {
        @CommandLine.Option(
                names = {"--formats", "formats"},
                description = "Display all formats supported by CATS generators",
                required = true)
        boolean formats;
    }

    static class ListStrategies {
        @CommandLine.Option(
                names = {"-s", "--fieldsFuzzerStrategies", "fieldsFuzzerStrategies"},
                description = "Display all current registered Fields Fuzzing Strategies",
                required = true)
        boolean strategies;
    }

    static class ListFuzzersGroup {
        @CommandLine.Option(
                names = {"-f", "--fuzzers", "fuzzers"},
                description = "Display all current registered Fuzzers")
        boolean fuzzers;

        @CommandLine.Option(
                names = {"-l", "--linters", "linters"},
                description = "Display all current registered Linters")
        boolean linters;
    }

    static class ListProfilesGroup {
        @CommandLine.Option(
                names = {"--profiles", "profiles"},
                description = "Display all current registered Profiles")
        boolean profiles;
    }

    static class ListMutatorsGroup {
        @CommandLine.Option(
                names = {"-m", "--mutators", "mutators"},
                description = "Display all current registered Mutators")
        boolean mutators;

        @CommandLine.Option(
                names = {"--cmt", "--customMutatorTypes"},
                description = "Display types supported by the Custom Mutator")
        boolean customMutatorTypes;
    }

    static class ListContractOptions {
        @CommandLine.Option(
                names = {"-p", "--paths", "paths"},
                description = "Display all paths from the OpenAPI contract/spec",
                required = true)
        boolean paths;

        @CommandLine.Option(names = {"--path"},
                description = "A path to display more info")
        private String path;

        @CommandLine.Option(names = {"--tag"},
                description = "Tag to filter paths")
        private String tag;

        @CommandLine.Option(
                names = {"-c", "--contract"},
                description = "The OpenAPI contract/spec",
                required = true)
        String contract;
    }

}
