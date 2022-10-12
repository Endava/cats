package com.endava.cats.fuzzer.http;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.net.URL;

@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class CheckDeletedResourcesNotAvailableFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CheckDeletedResourcesNotAvailableFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final CatsGlobalContext catsGlobalContext;

    public CheckDeletedResourcesNotAvailableFuzzer(SimpleExecutor simpleExecutor, CatsGlobalContext catsGlobalContext) {
        this.simpleExecutor = simpleExecutor;
        this.catsGlobalContext = catsGlobalContext;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() == HttpMethod.GET) {
            logger.info("Stored successful DELETE requests: {}", catsGlobalContext.getSuccessfulDeletes().size());
            for (String delete : catsGlobalContext.getSuccessfulDeletes()) {
                simpleExecutor.execute(
                        SimpleExecutorContext.builder()
                                .logger(logger)
                                .fuzzer(this)
                                .expectedResponseCode(ResponseCodeFamily.FOURXX)
                                .fuzzingData(data)
                                .payload("{}")
                                .path(getRelativePath(delete))
                                .scenario("Check that previously deleted resource is not available")
                                .build()
                );
            }
            catsGlobalContext.getSuccessfulDeletes().clear();
        }
    }

    static String getRelativePath(String url) {
        try {
            return new URL(url).getPath();
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public String description() {
        return "checks that resources are not available after successful deletes";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
