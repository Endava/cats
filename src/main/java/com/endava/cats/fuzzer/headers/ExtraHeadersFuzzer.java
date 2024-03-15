package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

import static com.endava.cats.util.CatsDSLWords.CATS_FUZZY_HEADER;

/**
 * Adds an extra header for each request.
 */
@Singleton
@HeaderFuzzer
public class ExtraHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ExtraHeadersFuzzer.class);

    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     */
    public ExtraHeadersFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<CatsHeader> headerSet = new HashSet<>(data.getHeaders());
        headerSet.add(CatsHeader.builder().name(CATS_FUZZY_HEADER).required(false).value(CATS_FUZZY_HEADER).build());

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzingData(data)
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .fuzzer(this)
                        .logger(logger)
                        .scenario("Add an extra header inside the request: name [%s], value [%s]. ".formatted(CATS_FUZZY_HEADER, CATS_FUZZY_HEADER))
                        .headers(headerSet)
                        .build()
        );
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and add an extra field inside the request called 'Cats-Fuzzy-Header'";
    }
}