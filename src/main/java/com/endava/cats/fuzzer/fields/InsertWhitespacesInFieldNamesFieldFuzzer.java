package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

@FieldFuzzer
@Singleton
public class InsertWhitespacesInFieldNamesFieldFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(InsertWhitespacesInFieldNamesFieldFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new InsertWhitespacesInFieldNamesFieldFuzzer instance.
     *
     * @param simpleExecutor the simple executor
     */
    public InsertWhitespacesInFieldNamesFieldFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skipping fuzzer for path {} because the payload is empty", data.getPath());
            return;
        }

        Set<String> allFieldsByHttpMethod = data.getAllFieldsByHttpMethod();
        String randomWhitespace = UnicodeGenerator.generateRandomUnicodeString(2, Character::isWhitespace);

        for (String field : allFieldsByHttpMethod) {
            logger.debug("Fuzzing field {}, inserting {}", field, randomWhitespace);
            if (JsonUtils.isFieldInJson(data.getPayload(), field)) {
                process(data, field, randomWhitespace);
            }
        }
    }

    private void process(FuzzingData data, String field, String randomWhitespace) {
        String fuzzedJson = JsonUtils.insertCharactersInFieldKey(data.getPayload(), field, randomWhitespace);

        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(this)
                .fuzzingData(data)
                .payload(fuzzedJson)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .scenario("Insert random whitespaces in the field name [" + field + "]")
                .build());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterates through each request field name and insert random whitespaces";
    }
}