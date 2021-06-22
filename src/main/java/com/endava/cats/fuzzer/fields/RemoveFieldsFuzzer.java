package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzer at fields level. It will remove different fields from the payload based on multiple strategies.
 */
@Component
@FieldFuzzer
@ConditionalOnProperty(value = "fuzzer.fields.RemoveFieldsFuzzer.enabled", havingValue = "true")
public class RemoveFieldsFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(RemoveFieldsFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;
    private final FilterArguments filterArguments;

    @Value("${fieldsFuzzingStrategy:ONEBYONE}")
    private String fieldsFuzzingStrategy;

    @Value("${maxFieldsToRemove:0}")
    private String maxFieldsToRemove;

    @Autowired
    public RemoveFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilterArguments fa) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
        this.filterArguments = fa;
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
                .filter(field -> !filterArguments.getSkippedFields().contains(field))
                .collect(Collectors.toSet());
    }

    private Set<Set<String>> getAllFields(FuzzingData data) {
        FuzzingData.SetFuzzingStrategy strategy = FuzzingData.SetFuzzingStrategy.valueOf(fieldsFuzzingStrategy);
        Set<Set<String>> sets = data.getAllFields(strategy, maxFieldsToRemove);

        LOGGER.info("Fuzzer will run with [{}] fields configuration possibilities out of [{}] maximum possible",
                sets.size(), (int) Math.pow(2, data.getAllFields().size()));

        return sets;
    }


    private void process(FuzzingData data, List<String> required, Set<String> subset) {
        String finalJsonPayload = this.getFuzzedJsonWithFieldsRemove(data.getPayload(), subset);

        if (!catsUtil.equalAsJson(finalJsonPayload, data.getPayload())) {
            testCaseListener.addScenario(LOGGER, "Remove the following fields from request: {}", subset);

            boolean hasRequiredFieldsRemove = this.hasRequiredFieldsRemove(required, subset);
            testCaseListener.addExpectedResult(LOGGER, "Should return [{}] response code as required fields [{}] removed", catsUtil.getExpectedWordingBasedOnRequiredFields(hasRequiredFieldsRemove));

            CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                    .payload(finalJsonPayload).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).build());
            testCaseListener.reportResult(LOGGER, data, response, catsUtil.getResultCodeBasedOnRequiredFieldsRemoved(hasRequiredFieldsRemove));
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

        if (catsUtil.isJsonArray(payload)) {
            prefix = CatsUtil.ALL_ELEMENTS_ROOT_ARRAY;
        }
        for (String field : fieldsToRemove) {
            payload = catsUtil.deleteNode(payload, prefix + field);
        }

        return payload;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
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
