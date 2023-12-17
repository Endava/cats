package com.endava.cats.command;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.command.model.FuzzerListEntry;
import com.endava.cats.command.model.PathDetailsEntry;
import com.endava.cats.command.model.PathListEntry;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import org.fusesource.jansi.Ansi;
import org.springframework.core.annotation.AnnotationUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * List various information such as: OpenAPI paths, fuzzers list, format supported by CATS and so on.
 */
@CommandLine.Command(
        name = "list",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "List Fuzzers, OpenAPI paths and FieldFuzzing strategies",
        versionProvider = VersionProvider.class)
@Unremovable
public class ListCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    private final List<Fuzzer> fuzzersList;

    private final List<String> formats;

    @CommandLine.ArgGroup(multiplicity = "1")
    ListCommandGroups listCommandGroups;
    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output to console in JSON format.")
    private boolean json;


    public ListCommand(@Any Instance<Fuzzer> fuzzersList, @Any Instance<OpenAPIFormat> formats) {
        this.fuzzersList = fuzzersList.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), ValidateAndTrim.class) == null)
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), ValidateAndSanitize.class) == null)
                .toList();
        this.formats = formats.stream().flatMap(format -> format.matchingFormats().stream()).toList();
    }

    @Override
    public void run() {
        if (listCommandGroups.listFuzzersGroup != null && listCommandGroups.listFuzzersGroup.fuzzers) {
            listFuzzers();
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
    }

    void listFormats() {
        if (json) {
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(formats));
        } else {
            logger.noFormat("Registered OpenAPI formats: {}", formats);
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
            logger.noFormat(path);
            for (PathDetailsEntry.OperationDetails operation : pathDetailsEntry.getOperations()) {
                logger.noFormat("  ");
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
        List<Fuzzer> contractInfo = filterFuzzers(LinterFuzzer.class);

        if (json) {
            List<FuzzerListEntry> fuzzerEntries = List.of(
                    new FuzzerListEntry().category("Field").fuzzers(fieldFuzzers),
                    new FuzzerListEntry().category("Header").fuzzers(headerFuzzers),
                    new FuzzerListEntry().category("HTTP").fuzzers(httpFuzzers),
                    new FuzzerListEntry().category("Linters").fuzzers(contractInfo));
            PrettyLoggerFactory.getConsoleLogger().noFormat(JsonUtils.GSON.toJson(fuzzerEntries));
        } else {
            String message = ansi().bold().fg(Ansi.Color.GREEN).a("CATS has {} registered fuzzers:").reset().toString();
            logger.noFormat(message, fuzzersList.size());
            displayFuzzers(fieldFuzzers, FieldFuzzer.class);
            displayFuzzers(headerFuzzers, HeaderFuzzer.class);
            displayFuzzers(httpFuzzers, HttpFuzzer.class);
            displayFuzzers(contractInfo, LinterFuzzer.class);
        }
    }

    List<Fuzzer> filterFuzzers(Class<? extends Annotation> annotation) {
        return fuzzersList.stream()
                .filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null)
                .sorted(Comparator.comparing(Object::toString))
                .toList();
    }

    void displayFuzzers(List<Fuzzer> fuzzers, Class<? extends Annotation> annotation) {
        String message = ansi().bold().fg(Ansi.Color.CYAN).a("{} {} Fuzzers:").reset().toString();
        String typeOfFuzzers = annotation.getSimpleName().replace("Fuzzer", "");
        logger.noFormat(" ");
        logger.noFormat(message, fuzzers.size(), typeOfFuzzers);
        fuzzers.stream().map(fuzzer -> " ◼ " + ansi().bold().fg(Ansi.Color.GREEN).a(ConsoleUtils.removeTrimSanitize(fuzzer.toString())).reset().a(" - " + fuzzer.description()).reset()).forEach(logger::noFormat);
    }

    static class ListCommandGroups {
        @CommandLine.ArgGroup(exclusive = false, heading = "List OpenAPI Contract Paths%n")
        ListContractOptions listContractOptions;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Fuzzers%n")
        ListFuzzersGroup listFuzzersGroup;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Fields Fuzzer Strategies%n")
        ListStrategies listStrategiesGroup;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Supported OpenAPI Formats%n")
        ListFormats listFormats;
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
                description = "Display all current registered Fields Fuzzer Strategies",
                required = true)
        boolean strategies;
    }

    static class ListFuzzersGroup {
        @CommandLine.Option(
                names = {"-f", "--fuzzers", "fuzzers"},
                description = "Display all current registered Fuzzers",
                required = true)
        boolean fuzzers;
    }

    static class ListContractOptions {
        @CommandLine.Option(
                names = {"-p", "--paths", "paths"},
                description = "Display all paths from the OpenAPI contract",
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
                description = "The OpenAPI contract",
                required = true)
        String contract;
    }

}
