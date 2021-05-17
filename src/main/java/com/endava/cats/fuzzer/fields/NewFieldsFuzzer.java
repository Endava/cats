package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
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
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "fuzzer.fields.NewFieldsFuzzer.enabled", havingValue = "true")
public class NewFieldsFuzzer implements Fuzzer {
    protected static final String NEW_FIELD = "catsFuzzyField";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(NewFieldsFuzzer.class);
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
        testCaseListener.addScenario(LOGGER, "Add new field inside the request: name [{}], value [{}]. All other details are similar to a happy flow", NEW_FIELD, NEW_FIELD);
        testCaseListener.addExpectedResult(LOGGER, "Should get a [{}] response code", expectedResultCode.asString());

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedJson.toString()).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).build());
        testCaseListener.reportResult(LOGGER, data, response, expectedResultCode);
    }

    protected JsonElement getFuzzedJson(FuzzingData data) {
        JsonElement jsonElement = JsonParser.parseString(data.getPayload());

        if (jsonElement instanceof JsonObject) {
            ((JsonObject) jsonElement).addProperty(NEW_FIELD, NEW_FIELD);
        } else {
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
