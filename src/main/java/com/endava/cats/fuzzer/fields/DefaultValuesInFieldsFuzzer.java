package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Singleton
@FieldFuzzer
public class DefaultValuesInFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;


    public DefaultValuesInFieldsFuzzer(FieldsIteratorExecutor ce) {
        this.catsExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<Schema<?>> isNotEnum = schema -> schema.getEnum() == null;
        Predicate<Schema<?>> hasDefault = schema -> schema.getDefault() != null;
        Function<Schema<?>, List<String>> fuzzedValueProducer = schema -> List.of(String.valueOf(schema.getDefault()));

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Iterate through each field with default value defined and send happy flow requests.")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamily.TWOXX)
                        .schemaFilter(isNotEnum.and(hasDefault))
                        .fieldFilter(catsExecutor::isFieldNotADiscriminator)
                        .fuzzValueProducer(fuzzedValueProducer)
                        .skipMessage("It does not have a defined default value.")
                        .logger(logger)
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
