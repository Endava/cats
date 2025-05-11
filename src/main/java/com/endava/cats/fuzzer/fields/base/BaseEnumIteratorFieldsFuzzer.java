package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Base class for fuzzers that iterate through enum values in fields.
 * It provides a common structure for fuzzing enum fields in API requests.
 */
public abstract class BaseEnumIteratorFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    protected final FieldsIteratorExecutor catsExecutor;

    /**
     * Constructor for initializing common dependencies for fuzzing enum fields.
     *
     * @param executor the executor used to perform the fuzzing
     */
    protected BaseEnumIteratorFieldsFuzzer(FieldsIteratorExecutor executor) {
        this.catsExecutor = executor;
    }

    /**
     * Produces fuzz values for the given schema and field.
     *
     * @return a BiFunction that takes a Schema and a field name, and returns a list of fuzz values
     */
    protected abstract BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer();

    /**
     * The description of the scenario being executed.
     *
     * @return a string describing the scenario
     */
    protected abstract String scenario();

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<Schema<?>> schemaFilter = schema -> schema.getEnum() != null;
        Predicate<String> notADiscriminator = catsExecutor::isFieldNotADiscriminator;
        Predicate<String> fieldExists = field -> JsonUtils.isFieldInJson(data.getPayload(), field);

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario(this.scenario())
                        .fuzzingData(data)
                        .fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .skipMessage("It's either not an enum or it's a discriminator.")
                        .fieldFilter(notADiscriminator.and(fieldExists))
                        .schemaFilter(schemaFilter)
                        .fuzzValueProducer(this.fuzzValueProducer())
                        .simpleReplaceField(true)
                        .logger(logger)
                        .fuzzer(this)
                        .build());
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
