package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.Predicate;

@FieldFuzzer
@Singleton
public class ReplacePrimitivesWithObjectsFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;

    public ReplacePrimitivesWithObjectsFieldsFuzzer(FieldsIteratorExecutor ce) {
        this.catsExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<String> fieldFilter = field -> JsonUtils.isPrimitive(data.getPayload(), field);

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Replace primitive fields with object values. ")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamily.FOURXX)
                        .skipMessage("Fuzzer only runs for primitives")
                        .fieldFilter(fieldFilter)
                        .fuzzValueProducer(schema -> List.of("{\"catsKey1\": \"catsValue1\", \"catsKey2\": 20}"))
                        .replaceRefData(false)
                        .logger(logger)
                        .fuzzer(this)
                        .build());
    }

    @Override
    public String description() {
        return "iterate through each primitive field and replace it with object values";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.GET, HttpMethod.DELETE);
    }

}