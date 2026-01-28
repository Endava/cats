package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
@FieldFuzzer
public class ZeroWidthCharsInNamesFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ZeroWidthCharsInNamesFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new ZeroWidthCharsInNamesFieldsFuzzer instance.
     *
     * @param simpleExecutor the simple executor
     */
    public ZeroWidthCharsInNamesFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skip fuzzer as payload is empty");
            return;
        }
        for (String fuzzValue : UnicodeGenerator.getZwCharsSmallListFields()) {
            for (String fuzzedField : data.getAllFieldsByHttpMethod()
                    .stream()
                    .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                    .limit(5)
                    .collect(Collectors.toSet())) {
                process(data, fuzzedField, fuzzValue);
            }
        }
    }

    private void process(FuzzingData data, String fuzzedField, String fuzzValue) {
        String fuzzedPayload = getFuzzedPayload(data, fuzzedField, fuzzValue);

        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(this)
                .fuzzingData(data)
                .payload(fuzzedPayload)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .scenario("Insert zero-width chars in field names: field [" + fuzzedField + "], char [" + FuzzingStrategy.formatValue(fuzzValue) + "]. All other details are similar to a happy flow")
                .build());
    }

    private static @NotNull String getFuzzedPayload(FuzzingData data, String fuzzedField, String fuzzValue) {
        String currentPayload = data.getPayload();
        String simpleFuzzedFieldName = fuzzedField.substring(fuzzedField.lastIndexOf("#") + 1);
        String fuzzedFieldName = CatsUtil.insertInTheMiddle(simpleFuzzedFieldName, fuzzValue, true);
        return currentPayload.replace("\"" + simpleFuzzedFieldName + "\"", "\"" + fuzzedFieldName + "\"");
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterate through each field and insert zero-width characters in the field names";
    }
}
