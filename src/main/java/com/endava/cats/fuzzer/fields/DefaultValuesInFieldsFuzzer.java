package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Fuzzer that send default values for each field, if defined.
 */
@Singleton
@FieldFuzzer
public class DefaultValuesInFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;

    /**
     * Creates a new DefaultValuesInFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public DefaultValuesInFieldsFuzzer(FieldsIteratorExecutor ce) {
        this.catsExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<Schema<?>> isNotEnum = schema -> schema.getEnum() == null;
        Predicate<Schema<?>> hasDefault = schema -> schema.getDefault() != null;
        BiFunction<Schema<?>, String, List<Object>> fuzzedValueProducer = (schema, field) -> List.of(schema.getDefault());
        Predicate<String> isNotDiscriminator = catsExecutor::isFieldNotADiscriminator;
        Predicate<String> isFieldInRequestPayload = field -> JsonUtils.isFieldInJson(data.getPayload(), field);

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Iterate through each field with default value defined and send happy flow requests.")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .schemaFilter(isNotEnum.and(hasDefault))
                        .fieldFilter(isNotDiscriminator.and(isFieldInRequestPayload))
                        .fuzzValueProducer(fuzzedValueProducer)
                        .skipMessage("It does not have a defined default value.")
                        .logger(logger)
                        .simpleReplaceField(true)
                        .fuzzer(this)
                        .build());
    }

    @Override
    public String description() {
        return "iterate through each field with default values defined and send a happy flow request";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
