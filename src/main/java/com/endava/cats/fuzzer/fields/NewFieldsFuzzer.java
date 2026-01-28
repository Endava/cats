package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
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
    private final SimpleExecutor simpleExecutor;


    /**
     * Creates a new NewFieldsFuzzer instance.
     *
     * @param simpleExecutor the simple executor
     */
    public NewFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        String fuzzedJson = this.addNewField(data);
        if (JsonUtils.equalAsJson(fuzzedJson, data.getPayload())) {
            logger.skip("Could not add new field to the payload. Skipping fuzzing");
            return;
        }

        ResponseCodeFamily expectedResultCode = ResponseCodeFamilyPredefined.TWOXX;
        if (HttpMethod.requiresBody(data.getMethod())) {
            expectedResultCode = ResponseCodeFamilyPredefined.FOURXX;
        }

        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(this)
                .fuzzingData(data)
                .payload(fuzzedJson)
                .expectedResponseCode(expectedResultCode)
                .scenario("Add new field inside the request: name [" + NEW_FIELD + "], value [" + NEW_FIELD + "]. All other details are similar to a happy flow")
                .build());
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
