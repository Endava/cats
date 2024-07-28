package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutator;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutatorConfig;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutatorKeywords;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Fuzzer intended for continuous fuzzing. It will randomly choose fields to fuzz and mutators to apply.
 * The Fuzzer will stop after one of the supplied stopXXX conditions is met: time elapsed, errors occurred or tests executed.
 */
@Singleton
@SpecialFuzzer
public class RandomFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RandomFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final MatchArguments matchArguments;
    private final StopArguments stopArguments;
    private final FilesArguments filesArguments;
    private final Instance<Mutator> mutators;

    @Inject
    public RandomFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                        ExecutionStatisticsListener executionStatisticsListener,
                        MatchArguments matchArguments, Instance<Mutator> mutators,
                        StopArguments stopArguments, FilesArguments filesArguments) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.executionStatisticsListener = executionStatisticsListener;
        this.matchArguments = matchArguments;
        this.mutators = mutators;
        this.stopArguments = stopArguments;
        this.filesArguments = filesArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.error("Skipping fuzzer as payload is empty");
            return;
        }
        List<Mutator> mutatorsToRun = this.getMutators();

        if (mutatorsToRun.isEmpty()) {
            logger.error("No Mutators to run! Enable debug for more details.");
            return;
        }

        long startTime = System.currentTimeMillis();

        boolean shouldStop = false;
        Set<String> allCatsFields = data.getAllFieldsByHttpMethod();

        testCaseListener.updateUnknownProgress(data);

        while (!shouldStop) {
            String targetField = CatsUtil.selectRandom(allCatsFields);
            logger.debug("Selected field to be mutated: [{}]", targetField);

            if (!JsonUtils.isFieldInJson(data.getPayload(), targetField)) {
                logger.debug("Field not in this payload, selecting another one...");
                continue;
            }

            Mutator selectedRandomMutator = CatsUtil.selectRandom(mutatorsToRun);
            logger.debug("Selected mutator [{}]", selectedRandomMutator.getClass().getSimpleName());
            
            String mutatedPayload = selectedRandomMutator.mutate(data.getPayload(), targetField);
            Collection<CatsHeader> mutatedHeaders = selectedRandomMutator.mutate(data.getHeaders());

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .fuzzer(this)
                            .fuzzingData(data)
                            .logger(logger)
                            .payload(mutatedPayload)
                            .headers(mutatedHeaders)
                            .scenario("Send a random payload mutating field [%s] with [%s] mutator".formatted(targetField, selectedRandomMutator.description()))
                            .expectedSpecificResponseCode("a response that doesn't match given --matchXXX arguments")
                            .responseProcessor(this::processResponse)
                            .build());

            testCaseListener.updateUnknownProgress(data);
            shouldStop = stopArguments.shouldStop(executionStatisticsListener.getErrors(), testCaseListener.getCurrentTestCaseNumber(), startTime);
        }
    }

    void processResponse(CatsResponse catsResponse, FuzzingData fuzzingData) {
        if (matchArguments.isMatchResponse(catsResponse)) {
            testCaseListener.reportResultError(logger, fuzzingData, "Response matches arguments", "Response matches" + matchArguments.getMatchString());
        } else {
            testCaseListener.skipTest(logger, "Skipping test as response does not match given matchers!");
        }
    }

    private List<Mutator> getMutators() {
        if (filesArguments.getMutatorsFolder() == null) {
            return mutators.stream().toList();
        }

        return this.parseMutators();
    }

    private List<Mutator> parseMutators() {
        List<Mutator> customMutators = new ArrayList<>();

        File mutatorsFolder = filesArguments.getMutatorsFolder();
        File[] customMutatorsFiles = mutatorsFolder.listFiles();

        if (customMutatorsFiles == null) {
            logger.error("Invalid custom Mutators folder {}", filesArguments.getMutatorsFolder().getAbsolutePath());
            return Collections.emptyList();
        }

        for (File customMutatorFile : Objects.requireNonNull(customMutatorsFiles)) {
            try {
                Map<String, Object> customMutator = parseYamlAsSimpleMap(customMutatorFile.getCanonicalPath());

                CustomMutatorConfig config = this.createConfig(customMutator);
                customMutators.add(new CustomMutator(config));
            } catch (Exception e) {
                logger.warn("There was a problem parsing {}: {}", customMutatorFile.getAbsolutePath(), e.toString());
            }
        }

        return List.copyOf(customMutators);
    }

    CustomMutatorConfig createConfig(Map<String, Object> customMutator) throws IOException {
        String name = String.valueOf(
                customMutator.get(
                        CustomMutatorKeywords.NAME.name().toLowerCase(Locale.ROOT)
                )
        );

        CustomMutatorConfig.Type type = CustomMutatorConfig.Type.valueOf(
                String.valueOf(
                        customMutator.get(
                                CustomMutatorKeywords.TYPE.name().toLowerCase(Locale.ROOT)
                        )
                ).toUpperCase(Locale.ROOT)
        );
        Object customMutatorValues = customMutator.get(CustomMutatorKeywords.VALUES.name().toLowerCase(Locale.ROOT));
        List<?> values;

        if (customMutatorValues instanceof List<?> valuesList) {
            logger.debug("Custom fuzzer values from mutator file");
            values = valuesList;
        } else {
            String fileLocation = String.valueOf(customMutatorValues);
            logger.debug("Loading custom fuzzer values from external file {}", fileLocation);
            values = readValueFromFile(fileLocation);
        }

        return new CustomMutatorConfig(name, type, values);
    }

    List<String> readValueFromFile(String fileLocation) throws IOException {
        return Files.readAllLines(Path.of(fileLocation))
                .stream()
                .filter(Predicate.not(String::isBlank))
                .filter(Predicate.not(line -> line.startsWith("#")))
                .toList();
    }

    static Map<String, Object> parseYamlAsSimpleMap(String yaml) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (Reader reader = new InputStreamReader(new FileInputStream(yaml), StandardCharsets.UTF_8)) {
            JsonNode node = mapper.reader().readTree(reader);
            return mapper.convertValue(node, new TypeReference<>() {
            });
        }
    }

    @Override
    public String description() {
        return "continuously fuzz random fields with random values based on registered mutators";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
