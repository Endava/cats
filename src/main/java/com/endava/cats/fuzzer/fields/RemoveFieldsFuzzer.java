package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
                sets.size(), (int) Math.pow(2, data.getAllProperties().size()));

        return sets;
    }


    private void process(FuzzingData data, List<String> required, Set<String> subset) {
        testCaseListener.addScenario(LOGGER, "Scenario: remove the following fields from request: {}", subset);
        JsonElement jsonObject = this.getFuzzedJsonWithFieldsRemove(data.getPayload(), subset);

        boolean hasRequiredFieldsRemove = this.hasRequiredFieldsRemove(required, subset);
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should return [{}] response code as required fields [{}] removed", catsUtil.getExpectedWordingBasedOnRequiredFields(hasRequiredFieldsRemove));

        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders()).payload(jsonObject.toString()).build());
        testCaseListener.reportResult(LOGGER, data, response, catsUtil.getResultCodeBasedOnRequiredFieldsRemoved(hasRequiredFieldsRemove));
    }

    private boolean hasRequiredFieldsRemove(List<String> required, Set<String> subset) {
        Set<String> intersection = new HashSet<>(required);
        intersection.retainAll(subset);
        return !intersection.isEmpty();
    }


    private JsonElement getFuzzedJsonWithFieldsRemove(String payload, Set<String> fieldsToRemove) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(payload);

        if (jsonElement.isJsonObject()) {
            this.removeCurrentSet(fieldsToRemove, jsonElement);
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                this.removeCurrentSet(fieldsToRemove, element);
            }
        }

        return jsonElement;
    }

    private void removeCurrentSet(Set<String> currentSet, JsonElement jsonElement) {
        for (String field : currentSet) {
            String[] depth = field.split("#");
            JsonElement element = catsUtil.getJsonElementBasedOnFullyQualifiedName(jsonElement, field);

            if (element != null) {
                element.getAsJsonObject().remove(depth[depth.length - 1]);
            }
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
