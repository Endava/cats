package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fuzzer at fields level. It will remove different fields from the payload based on multiple strategies.
 */
@Component
@FieldFuzzer
public class RemoveFieldsFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveFieldsFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;

    @Value("${fieldsFuzzingStrategy:ONEBYONE}")
    private String fieldsFuzzingStrategy;

    @Value("${maxFieldsToRemove:0}")
    private String maxFieldsToRemove;

    @Autowired
    public RemoveFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
    }

    public void fuzz(FuzzingData data) {
        LOGGER.info("All required fields, including subfields: {}", data.getAllRequiredFields());
        Set<Set<String>> sets = this.getAllFields(data);

        for (Set<String> subset : sets) {
            testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data, data.getAllRequiredFields(), subset));
        }
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
            testCaseListener.addScenario(LOGGER, "Scenario: remove the following fields from request: {}", subset);

            boolean hasRequiredFieldsRemove = this.hasRequiredFieldsRemove(required, subset);
            testCaseListener.addExpectedResult(LOGGER, "Expected result: should return [{}] response code as required fields [{}] removed", catsUtil.getExpectedWordingBasedOnRequiredFields(hasRequiredFieldsRemove));

            CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                    .payload(finalJsonPayload).queryParams(data.getQueryParams()).build());
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
            payload = this.deleteNode(payload, prefix + field);
        }

        return payload;
    }

    public String deleteNode(String payload, String node) {
        try {
            return JsonPath.parse(payload).delete(catsUtil.sanitizeToJsonPath(node)).jsonString();
        } catch (PathNotFoundException e) {
            return payload;
        }
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate trough each request fields and remove certain fields according to the supplied 'fieldsFuzzingStrategy'";
    }
}
