package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.json.JsonUtils;
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
public class IterateThroughEnumValuesFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;


    public IterateThroughEnumValuesFieldsFuzzer(FieldsIteratorExecutor ce) {
        this.catsExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Predicate<Schema<?>> schemaFilter = schema -> schema.getEnum() != null;
        Function<Schema<?>, List<String>> fuzzValueProducer = schema -> schema.getEnum().stream().map(String::valueOf).toList();
        Predicate<String> notADiscriminator = catsExecutor::isFieldNotADiscriminator;
        Predicate<String> fieldExists = field -> JsonUtils.isFieldInJson(data.getPayload(), field);

        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Iterate through each possible enum values and send happy flow requests.")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamily.TWOXX)
                        .skipMessage("It's either not an enum or it's a discriminator.")
                        .fieldFilter(notADiscriminator.and(fieldExists))
                        .schemaFilter(schemaFilter)
                        .fuzzValueProducer(fuzzValueProducer)
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
