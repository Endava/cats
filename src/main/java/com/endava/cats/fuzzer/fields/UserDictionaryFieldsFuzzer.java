package com.endava.cats.fuzzer.fields;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;

@Singleton
@FieldFuzzer
public class UserDictionaryFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor catsExecutor;
    private final UserArguments userArguments;
    private final MatchArguments matchArguments;

    public UserDictionaryFieldsFuzzer(FieldsIteratorExecutor ce, UserArguments ua, MatchArguments ma) {
        this.catsExecutor = ce;
        this.userArguments = ua;
        this.matchArguments = ma;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (userArguments.getWords() == null) {
            logger.error("Skipping fuzzer as --words was not provided!");
        } else if (!matchArguments.isAnyMatchArgumentSupplied()) {
            logger.error("Skipping fuzzer as no --m* argument was provided!");
        } else {
            Function<Schema<?>, List<String>> fuzzedValueProducer = schema -> userArguments.getWordsAsList();

            catsExecutor.execute(
                    FieldsIteratorExecutorContext.builder()
                            .scenario("Iterate through each field and send values from user dictionary.")
                            .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                            .fuzzValueProducer(fuzzedValueProducer)
                            .fuzzer(this)
                            .logger(logger)
                            .build());
        }
    }

    @Override
    public String description() {
        return "iterates through each request fields and sends values from the user supplied dictionary";
    }

    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
