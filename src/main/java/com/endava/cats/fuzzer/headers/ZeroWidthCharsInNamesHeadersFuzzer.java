package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzes HTTP headers by injecting zero-width characters in the names.
 */
@HeaderFuzzer
@Singleton
public class ZeroWidthCharsInNamesHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ZeroWidthCharsInNamesHeadersFuzzer.class);

    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     */
    public ZeroWidthCharsInNamesHeadersFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getHeaders().isEmpty()) {
            return;
        }
        for (String zeroWidthChar : UnicodeGenerator.getZwCharsSmallList()) {
            Set<CatsHeader> clonedHeaders = data.getHeaders().stream()
                    .map(catsHeader -> CatsHeader.builder()
                            .name(CatsUtil.insertInTheMiddle(catsHeader.getName(), zeroWidthChar, true))
                            .value(catsHeader.getValue())
                            .required(catsHeader.isRequired())
                            .build())
                    .collect(Collectors.toSet());

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .fuzzingData(data)
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                            .fuzzer(this)
                            .logger(logger)
                            .scenario("Inject zero-width characters in the header names")
                            .headers(clonedHeaders)
                            .build()
            );
        }
    }


    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "injects zero-width characters in the header names";
    }
}
