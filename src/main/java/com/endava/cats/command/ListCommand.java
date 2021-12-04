package com.endava.cats.command;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.fuzzer.SpecialFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.fusesource.jansi.Ansi;
import org.springframework.core.annotation.AnnotationUtils;
import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

@CommandLine.Command(
        name = "list",
        mixinStandardHelpOptions = true,
        usageHelpWidth = 100,
        description = "List Fuzzers, OpenAPI paths and FieldFuzzing strategies",
        helpCommand = true,
        version = "cats list 7.0.0")
@Dependent
public class ListCommand implements Runnable {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(ListCommand.class);
    private final Instance<Fuzzer> fuzzersList;

    @CommandLine.ArgGroup(multiplicity = "1")
    ListCommandGroups listCommandGroups;

    public ListCommand(@Any Instance<Fuzzer> fuzzersList) {
        this.fuzzersList = fuzzersList;
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
    }

    void listContractPaths() {
        try {
            OpenAPI openAPI = CatsUtil.readOpenApi(listCommandGroups.listContractOptions.contract);
            LOGGER.star("Available paths:");
            openAPI.getPaths().keySet().stream().sorted().map(item -> "\t " + item).forEach(LOGGER::info);
        } catch (IOException e) {
            LOGGER.error("Error while reading contract: {}", e.getMessage());
        }
    }

    void listFuzzerStrategies() {
        LOGGER.info("Registered fieldsFuzzerStrategies: {}", Arrays.asList(FuzzingData.SetFuzzingStrategy.values()));
    }

    void listFuzzers() {
        String message = ansi().bold().fg(Ansi.Color.GREEN).a("CATS has {} registered fuzzers:").reset().toString();
        LOGGER.info(message, fuzzersList.stream().count());
        filterAndDisplay(FieldFuzzer.class);
        filterAndDisplay(HeaderFuzzer.class);
        filterAndDisplay(HttpFuzzer.class);
        filterAndDisplay(ContractInfoFuzzer.class);
        filterAndDisplay(SpecialFuzzer.class);
    }

    void filterAndDisplay(Class<? extends Annotation> annotation) {
        List<Fuzzer> fieldFuzzers = fuzzersList.stream().filter(fuzzer -> AnnotationUtils.findAnnotation(fuzzer.getClass(), annotation) != null).collect(Collectors.toList());
        String message = ansi().bold().fg(Ansi.Color.CYAN).a("{} {} Fuzzers:").reset().toString();
        String typeOfFuzzers = annotation.getSimpleName().replace("Fuzzer", "");
        LOGGER.info(" ");
        LOGGER.info(message, fieldFuzzers.size(), typeOfFuzzers);
        fieldFuzzers.stream().map(fuzzer -> "\t ◼ " + ansi().bold().fg(Ansi.Color.GREEN).a(fuzzer.toString()).reset().a(" - " + fuzzer.description()).reset()).forEach(LOGGER::info);
    }

    static class ListCommandGroups {
        @CommandLine.ArgGroup(exclusive = false, heading = "List OpenAPI contract Paths%n")
        ListContractOptions listContractOptions;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Fuzzers%n")
        ListFuzzersGroup listFuzzersGroup;

        @CommandLine.ArgGroup(exclusive = false, heading = "List Fields Fuzzer Strategies%n")
        ListStrategies listStrategiesGroup;
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

        @CommandLine.Option(
                names = {"-c", "--contract"},
                description = "The OpenAPI contract",
                required = true)
        String contract;
    }

}
