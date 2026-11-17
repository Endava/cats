package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzer at fields level. It will remove different fields from the payload based on multiple strategies.
 */
@Singleton
@FieldFuzzer
public class RemoveFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RemoveFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final FilterArguments filterArguments;
    private final ProcessingArguments processingArguments;


    /**
     * Creates a new RemoveFieldsFuzzer instance.
     *
     * @param simpleExecutor the simple executor
     * @param fa             filter arguments
     * @param pa             to get the number of max fields to remove at once
     */
    public RemoveFieldsFuzzer(SimpleExecutor simpleExecutor, FilterArguments fa, ProcessingArguments pa) {
        this.simpleExecutor = simpleExecutor;
        this.filterArguments = fa;
        this.processingArguments = pa;
    }

    @Override
    public void fuzz(FuzzingData data) {
        logger.debug("All required fields, including subfields: {}", data.getAllRequiredFields());
        Set<Set<String>> sets = this.getAllFields(data);
        RemovalContext context = createContext(data);

        for (Set<String> subset : sets) {
            Set<String> finalSubset = this.removeIfSkipped(subset);
            if (!finalSubset.isEmpty()) {
                process(context, finalSubset);
            }
        }
    }

    private RemovalContext createContext(FuzzingData data) {
        String payload = data.getPayload();
        String prefix = JsonUtils.isJsonArray(payload) ? JsonUtils.ALL_ELEMENTS_ROOT_ARRAY : "";
        return new RemovalContext(data, prefix, toComparablePayload(payload), new HashSet<>());
    }

    private Set<String> removeIfSkipped(Set<String> subset) {
        return subset.stream()
                .filter(field -> !filterArguments.getSkipFields().contains(field))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Set<String>> getAllFields(FuzzingData data) {
        FuzzingData.SetFuzzingStrategy strategy = processingArguments.getFieldsFuzzingStrategy();
        int interactionStrength = processingArguments.getInteractionStrength();
        Set<Set<String>> sets = strategy == FuzzingData.SetFuzzingStrategy.PAIRWISE && interactionStrength > 2
                ? data.getAllFields(strategy, processingArguments.getMaxFieldsToRemove(), interactionStrength)
                : data.getAllFields(strategy, processingArguments.getMaxFieldsToRemove());

        logger.note("Fuzzer will run with [{}] fields configuration possibilities out of [{}] maximum possible",
                sets.size(), (int) Math.pow(2, data.getAllFieldsByHttpMethod().size()));

        return sets;
    }

    private void process(RemovalContext context, Set<String> subset) {
        FuzzingData data = context.data();
        logger.debug("Payload {} and fields to remove {}", data.getPayload(), subset);
        String finalJsonPayload = this.getFuzzedJsonWithFieldsRemove(data.getPayload(), context.prefix(), subset);
        Object comparablePayload = finalJsonPayload.equals(data.getPayload())
                ? context.baseline()
                : toComparablePayload(finalJsonPayload);

        if (comparablePayload.equals(context.baseline())) {
            logger.skip("Field is from a different ANY_OF or ONE_OF payload. Skipping test");
            return;
        }
        if (!context.executedPayloads().add(comparablePayload)) {
            logger.skip("Payload after removing fields {} is identical to an already executed payload. Skipping test", subset);
            return;
        }
        executeSubset(data, data.getAllRequiredFields(), subset, finalJsonPayload);
    }

    private static Object toComparablePayload(String payload) {
        if (payload == null) {
            return "";
        }
        try {
            JsonNode node = JsonUtils.getSimpleObjectMapper().readTree(payload);
            if (node != null && !node.isMissingNode()) {
                return node;
            }
        } catch (Exception _) {
            // Fall through to case-insensitive string comparison for non-JSON payloads
        }
        return payload.toLowerCase(Locale.ROOT);
    }

    private void executeSubset(FuzzingData data, List<String> required, Set<String> subset, String finalJsonPayload) {
        boolean hasRequiredFieldsRemove = this.hasRequiredFieldsRemove(required, subset);
        Object[] expectedWording = ResponseCodeFamilyPredefined.getExpectedWordingBasedOnRequiredFields(hasRequiredFieldsRemove);

        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(this)
                .fuzzingData(data)
                .payload(finalJsonPayload)
                .mutationTargets(RequestTargetResolver.resolvePayloadFields(data, subset))
                .expectedResponseCode(ResponseCodeFamilyPredefined.from(String.valueOf(expectedWording[0])))
                .scenario("Remove the following fields from request: " + subset)
                .expectedResult(" as required fields %s removed".formatted(expectedWording[1]))
                .build());
    }

    private boolean hasRequiredFieldsRemove(List<String> required, Set<String> subset) {
        Set<String> intersection = new HashSet<>(required);
        intersection.retainAll(subset);
        return !intersection.isEmpty();
    }

    private String getFuzzedJsonWithFieldsRemove(String payload, String prefix, Set<String> fieldsToRemove) {
        for (String field : fieldsToRemove) {
            payload = JsonUtils.deleteNode(payload, prefix + field);
        }

        return payload;
    }

    private record RemovalContext(FuzzingData data, String prefix, Object baseline, Set<Object> executedPayloads) {
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate trough each request fields and remove certain fields according to the supplied 'fieldsFuzzingStrategy'";
    }
}
