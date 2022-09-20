package com.endava.cats.fuzzer.fields;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.CatsExecutor;
import com.endava.cats.fuzzer.executor.CatsExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
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
    private final CatsExecutor catsExecutor;


    public IterateThroughEnumValuesFieldsFuzzer(CatsExecutor ce) {
        this.catsExecutor = ce;
    }

    public void fuzz(FuzzingData data) {
        Predicate<Schema<?>> schemaFilter = schema -> schema.getEnum() != null;
        Predicate<String> fieldFilter = field -> catsExecutor.getTestCaseListener().isFieldNotADiscriminator(field);
        Function<Schema<?>, List<String>> fuzzValueProducer = schema -> schema.getEnum().stream().map(String::valueOf).toList();

        catsExecutor.execute(
                CatsExecutorContext.builder()
                        .scenario("Iterate through each possible enum values and send happy flow requests.")
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamily.TWOXX)
                        .skipMessage("It's either not an enum or it's a discriminator.")
                        .fieldFilter(fieldFilter)
                        .schemaFilter(schemaFilter)
                        .fuzzValueProducer(fuzzValueProducer)
                        .logger(logger)
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
