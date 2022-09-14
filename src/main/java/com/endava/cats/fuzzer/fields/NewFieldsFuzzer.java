package com.endava.cats.fuzzer.fields;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;

import static com.endava.cats.dsl.CatsDSLWords.NEW_FIELD;

@Singleton
@FieldFuzzer
public class NewFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(NewFieldsFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    public NewFieldsFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(logger, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        JsonElement fuzzedJson = this.addNewField(data);

        ResponseCodeFamily expectedResultCode = ResponseCodeFamily.TWOXX;
        if (HttpMethod.requiresBody(data.getMethod())) {
            expectedResultCode = ResponseCodeFamily.FOURXX;
        }
        testCaseListener.addScenario(logger, "Add new field inside the request: name [{}], value [{}]. All other details are similar to a happy flow", NEW_FIELD, NEW_FIELD);
        testCaseListener.addExpectedResult(logger, "Should get a [{}] response code", expectedResultCode.asString());

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedJson.toString()).queryParams(data.getQueryParams()).httpMethod(data.getMethod())
                .contentType(data.getFirstRequestContentType()).build());
        testCaseListener.reportResult(logger, data, response, expectedResultCode);
    }

    protected JsonElement addNewField(FuzzingData data) {
        JsonElement jsonElement = JsonParser.parseString(data.getPayload());

        if (jsonElement instanceof JsonObject jsonObject) {
            jsonObject.addProperty(NEW_FIELD, NEW_FIELD);
        } else {
            for (JsonElement element : (JsonArray) jsonElement) {
                ((JsonObject) element).addProperty(NEW_FIELD, NEW_FIELD);
            }
        }

        return jsonElement;
    }

    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a happy flow request and add a new field inside the request called 'catsFuzzyField'";
    }
}
