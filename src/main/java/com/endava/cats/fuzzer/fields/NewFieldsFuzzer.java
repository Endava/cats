package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NewFieldsFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewFieldsFuzzer.class);
    private static final String NEW_FIELD = "catsFuzzyField";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;

    @Autowired
    public NewFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        JsonElement fuzzedJson = this.getFuzzedJson(data);

        ResponseCodeFamily expectedResultCode = ResponseCodeFamily.TWOXX;
        if (catsUtil.isHttpMethodWithPayload(data.getMethod())) {
            expectedResultCode = ResponseCodeFamily.FOURXX;
        }
        testCaseListener.addScenario(LOGGER, "Scenario: add new field inside the request: name [{}], value [{}]. All other details are similar to a happy flow", NEW_FIELD, NEW_FIELD);
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should get a [{}] response code", expectedResultCode.asString());

        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedJson.toString()).queryParams(data.getQueryParams()).build());
        testCaseListener.reportResult(LOGGER, data, response, expectedResultCode);
    }

    private JsonElement getFuzzedJson(FuzzingData data) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(data.getPayload());

        if (jsonElement instanceof JsonObject) {
            ((JsonObject) jsonElement).addProperty(NEW_FIELD, NEW_FIELD);
        } else if (jsonElement instanceof JsonArray) {
            for (JsonElement element : (JsonArray) jsonElement) {
                ((JsonObject) element).addProperty(NEW_FIELD, NEW_FIELD);
            }
        }

        return jsonElement;
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and add a new field inside the request called 'catsFuzzyField'";
    }
}
