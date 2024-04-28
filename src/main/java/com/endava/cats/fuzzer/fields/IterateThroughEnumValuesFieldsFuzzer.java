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
 * Fields that iterates through enum values and sends one request per each value.
 */
@Singleton
@FieldFuzzer
public class IterateThroughEnumValuesFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;

    /**
     * Creates a new IterateThroughEnumValuesFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public IterateThroughEnumValuesFieldsFuzzer(FieldsIteratorExecutor ce) {
        this.catsExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<Schema<?>> schemaFilter = schema -> schema.getEnum() != null;
        BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer = (schema, field) -> schema.getEnum()
                .stream()
                .map(Object.class::cast)
                .toList();
        Predicate<String> notADiscriminator = catsExecutor::isFieldNotADiscriminator;
        Predicate<String> fieldExists = field -> JsonUtils.isFieldInJson(data.getPayload(), field);

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Iterate through each possible enum values and send happy flow requests.")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .skipMessage("It's either not an enum or it's a discriminator.")
                        .fieldFilter(notADiscriminator.and(fieldExists))
                        .schemaFilter(schemaFilter)
                        .fuzzValueProducer(fuzzValueProducer)
                        .simpleReplaceField(true)
                        .logger(logger)
                        .fuzzer(this)
                        .build());
    }

    @Override
    public String description() {
        return "iterate through each enum field and send happy flow requests iterating through each possible enum values";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
