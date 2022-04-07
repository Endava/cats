package com.endava.cats.fuzzer.fields;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.model.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
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
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(RemoveFieldsFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final IgnoreArguments ignoreArguments;
    private final ProcessingArguments processingArguments;

    public RemoveFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, IgnoreArguments fa, ProcessingArguments pa) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.ignoreArguments = fa;
        this.processingArguments = pa;
    }

    public void fuzz(FuzzingData data) {
        LOGGER.info("All required fields, including subfields: {}", data.getAllRequiredFields());
        Set<Set<String>> sets = this.getAllFields(data);

        for (Set<String> subset : sets) {
            Set<String> finalSubset = this.removeIfSkipped(subset);
            if (!finalSubset.isEmpty()) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data, data.getAllRequiredFields(), finalSubset));
            }
        }
    }

    private Set<String> removeIfSkipped(Set<String> subset) {
        return subset.stream()
                .filter(field -> !ignoreArguments.getSkipFields().contains(field))
                .collect(Collectors.toSet());
    }

    private Set<Set<String>> getAllFields(FuzzingData data) {
        Set<Set<String>> sets = data.getAllFields(FuzzingData.SetFuzzingStrategy.valueOf(processingArguments.getFieldsFuzzingStrategy().name())
                , processingArguments.getMaxFieldsToRemove());

        LOGGER.info("Fuzzer will run with [{}] fields configuration possibilities out of [{}] maximum possible",
                sets.size(), (int) Math.pow(2, data.getAllFieldsByHttpMethod().size()));

        return sets;
    }


    private void process(FuzzingData data, List<String> required, Set<String> subset) {
        String finalJsonPayload = this.getFuzzedJsonWithFieldsRemove(data.getPayload(), subset);

        if (!JsonUtils.equalAsJson(finalJsonPayload, data.getPayload())) {
            testCaseListener.addScenario(LOGGER, "Remove the following fields from request: {}", subset);

            boolean hasRequiredFieldsRemove = this.hasRequiredFieldsRemove(required, subset);
            testCaseListener.addExpectedResult(LOGGER, "Should return [{}] response code as required fields [{}] removed", ResponseCodeFamily.getExpectedWordingBasedOnRequiredFields(hasRequiredFieldsRemove));

            CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                    .payload(finalJsonPayload).queryParams(data.getQueryParams()).httpMethod(data.getMethod())
                    .contentType(data.getFirstRequestContentType()).build());
            testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.getResultCodeBasedOnRequiredFieldsRemoved(hasRequiredFieldsRemove));
        } else {
            testCaseListener.skipTest(LOGGER, "Field is from a different ANY_OF or ONE_OF payload");
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
        return this.getClass().getSimpleName().replace("_Subclass", "");
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
