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
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

        for (Set<String> subset : sets) {
            Set<String> finalSubset = this.removeIfSkipped(subset);
            if (!finalSubset.isEmpty()) {
                process(data, data.getAllRequiredFields(), finalSubset);
            }
        }
    }

    private Set<String> removeIfSkipped(Set<String> subset) {
        return subset.stream()
                .filter(field -> !filterArguments.getSkipFields().contains(field))
                .collect(Collectors.toSet());
    }

    private Set<Set<String>> getAllFields(FuzzingData data) {
        Set<Set<String>> sets = data.getAllFields(FuzzingData.SetFuzzingStrategy.valueOf(processingArguments.getFieldsFuzzingStrategy().name())
                , processingArguments.getMaxFieldsToRemove());

        logger.note("Fuzzer will run with [{}] fields configuration possibilities out of [{}] maximum possible",
                sets.size(), (int) Math.pow(2, data.getAllFieldsByHttpMethod().size()));

        return sets;
    }


    private void process(FuzzingData data, List<String> required, Set<String> subset) {
        logger.debug("Payload {} and fields to remove {}", data.getPayload(), subset);
        String finalJsonPayload = this.getFuzzedJsonWithFieldsRemove(data.getPayload(), subset);

        if (!JsonUtils.equalAsJson(finalJsonPayload, data.getPayload())) {
            boolean hasRequiredFieldsRemove = this.hasRequiredFieldsRemove(required, subset);
            Object[] expectedWording = ResponseCodeFamilyPredefined.getExpectedWordingBasedOnRequiredFields(hasRequiredFieldsRemove);

            simpleExecutor.execute(SimpleExecutorContext.builder()
                    .logger(logger)
                    .fuzzer(this)
                    .fuzzingData(data)
                    .payload(finalJsonPayload)
                    .expectedResponseCode(ResponseCodeFamilyPredefined.from(String.valueOf(expectedWording[0])))
                    .scenario("Remove the following fields from request: " + subset.toString())
                    .expectedResult(String.format(" as required fields %s removed", expectedWording[1]))
                    .build());
        } else {
            logger.skip("Field is from a different ANY_OF or ONE_OF payload. Skipping test");
        }
    }

    private boolean hasRequiredFieldsRemove(List<String> required, Set<String> subset) {
        Set<String> intersection = new HashSet<>(required);
        intersection.retainAll(subset);
        return !intersection.isEmpty();
    }


    private String getFuzzedJsonWithFieldsRemove(String payload, Set<String> fieldsToRemove) {
        String prefix = "";

        if (JsonUtils.isJsonArray(payload)) {
            prefix = JsonUtils.ALL_ELEMENTS_ROOT_ARRAY;
        }
        for (String field : fieldsToRemove) {
            payload = JsonUtils.deleteNode(payload, prefix + field);
        }

        return payload;
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
