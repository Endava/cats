package com.endava.cats.fuzzer.headers;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutorContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@HeaderFuzzer
public class UserDictionaryHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(UserDictionaryHeadersFuzzer.class);
    private final MatchArguments matchArguments;
    private final HeadersIteratorExecutor headersIteratorExecutor;

    private final UserArguments userArguments;

    @Inject
    public UserDictionaryHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor, MatchArguments matchArguments, UserArguments userArguments) {
        this.headersIteratorExecutor = headersIteratorExecutor;
        this.matchArguments = matchArguments;
        this.userArguments = userArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (userArguments.getWords() == null) {
            logger.error("Skipping fuzzer as --words was not provided!");
        } else if (!matchArguments.isAnyMatchArgumentSupplied()) {
            logger.error("Skipping fuzzer as no --m* argument was provided!");
        } else {
            headersIteratorExecutor.execute(
                    HeadersIteratorExecutorContext.builder()
                            .fuzzer(this)
                            .logger(logger)
                            .fuzzingData(data)
                            .fuzzValueProducer(() -> userArguments.getWordsAsList().stream().map(word -> FuzzingStrategy.replace().withData(word)).toList())
                            .scenario("Iterate through each header and send values from user dictionary.")
                            .build()
            );
        }
    }

    @Override
    public String description() {
        return "iterates through each request headers and sends values from the user supplied dictionary";
    }

    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
