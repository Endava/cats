package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Send duplicate headers either duplicating an existing header or duplicating a dummy one.
 */
@Singleton
@HeaderFuzzer
public class DuplicateHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(DuplicateHeadersFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     */
    public DuplicateHeadersFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        List<CatsHeader> headers = new ArrayList<>(data.getHeaders());
        CatsHeader header = CatsHeader.builder().name(CatsDSLWords.CATS_FUZZY_HEADER).required(false).value(CatsDSLWords.CATS_FUZZY_HEADER).build();

        if (headers.isEmpty()) {
            logger.skip("No headers to fuzz. Adding default: %s".formatted(CatsDSLWords.CATS_FUZZY_HEADER));
            headers.add(header);
        }

        for (CatsHeader catsHeader : headers) {
            List<CatsHeader> finalHeadersList = new ArrayList<>(headers);
            finalHeadersList.add(catsHeader.copy());
            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .headers(finalHeadersList)
                            .fuzzingData(data)
                            .logger(logger)
                            .expectedResponseCode(ResponseCodeFamily.FOURXX)
                            .scenario("Add a duplicate header inside the request: name [%s], value [%s]. All other details are similar to a happy flow".formatted(catsHeader.getName(), catsHeader.getTruncatedValue()))
                            .fuzzer(this)
                            .build());
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and duplicate an existing header";
    }
}