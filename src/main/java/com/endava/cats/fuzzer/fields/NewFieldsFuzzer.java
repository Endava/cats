package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import static com.endava.cats.util.CatsDSLWords.NEW_FIELD;

/**
 * Fuzzer that adds new fields in requests.
 */
@Singleton
@FieldFuzzer
public class NewFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(NewFieldsFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new NewFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     */
    public NewFieldsFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(logger, this, () -> process(data), data);
    }

    private void process(FuzzingData data) {
        String fuzzedJson = this.addNewField(data);
        if (JsonUtils.equalAsJson(fuzzedJson, data.getPayload())) {
            testCaseListener.skipTest(logger, "Could not fuzz the payload");
            return;
        }

        ResponseCodeFamily expectedResultCode = ResponseCodeFamilyPredefined.TWOXX;
        if (HttpMethod.requiresBody(data.getMethod())) {
            expectedResultCode = ResponseCodeFamilyPredefined.FOURXX;
        }
        testCaseListener.addScenario(logger, "Add new field inside the request: name [{}], value [{}]. All other details are similar to a happy flow", NEW_FIELD, NEW_FIELD);
        testCaseListener.addExpectedResult(logger, "Should get a [{}] response code", expectedResultCode.asString());

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedJson).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).contractPath(data.getContractPath())
                .contentType(data.getFirstRequestContentType()).pathParamsPayload(data.getPathParamsPayload())
                .build());
        testCaseListener.reportResult(logger, data, response, expectedResultCode);
    }

    String addNewField(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            return "{\"" + NEW_FIELD + "\":\"" + NEW_FIELD + "\"}";
        }

        JsonElement jsonElement = JsonParser.parseString(data.getPayload());

        if (jsonElement instanceof JsonObject jsonObject) {
            jsonObject.addProperty(NEW_FIELD, NEW_FIELD);
        } else if (jsonElement instanceof JsonArray jsonArray) {
            for (JsonElement element : jsonArray) {
                if (element instanceof JsonObject jsonObject) {
                    jsonObject.addProperty(NEW_FIELD, NEW_FIELD);
                }
            }
        }

        return jsonElement.toString();
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a happy flow request and add a new field inside the request called 'catsFuzzyField'";
    }
}
