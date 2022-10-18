package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.List;
import java.util.function.Predicate;

@FieldFuzzer
@Singleton
public class ReplaceObjectsWithPrimitivesFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;

    public ReplaceObjectsWithPrimitivesFieldsFuzzer(FieldsIteratorExecutor ce) {
        this.catsExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<String> fieldFilter = field -> JsonUtils.isObject(data.getPayload(), field);

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Replace non-primitive fields with primitive values. ")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamily.FOURXX)
                        .skipMessage("Fuzzer only runs for objects")
                        .fieldFilter(fieldFilter)
                        .fuzzValueProducer(schema -> List.of("cats_primitive_string"))
                        .replaceRefData(false)
                        .logger(logger)
                        .fuzzer(this)
                        .build());
    }

    @Override
    public String description() {
        return "iterate through each non-primitive field and replace it with primitive values";
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
