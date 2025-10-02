package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * Fuzzer that sends dummy invalid JSON bodies.
 */
@Singleton
@HttpFuzzer
public class RandomDummyInvalidJsonBodyFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private static final List<String> PAYLOADS = List.of("{0}", "{0.0}", "[{}]", "{$}",
            """ 
                    {"circularRef": {"self": {"$ref": "#/circularRef"}}}
                    """,
            """
                    {"backslash": "\\"}
                    """,
            """
                    {"ünicode": "ünicode"}
                    """,
            """
                    "{"unexpected" $ "token": "value"}
                    """,
            """
                    {\u0000:\u0000}
                    """,
            """
                    {"\u0000":"\u0000"}
                    """,
            """
                    {"␀":"␀"}
                    """,
            """
                    {␀:␀}
                    """);

    /**
     * Create a new RandomDummyInvalidJsonBodyFuzzer instance.
     *
     * @param ce the executor
     */
    @Inject
    RandomDummyInvalidJsonBodyFuzzer(SimpleExecutor ce) {
        this.simpleExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        for (String payload : PAYLOADS) {
            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                            .fuzzingData(data)
                            .logger(logger)
                            .replaceRefData(false)
                            .scenario("Send %s as invalid json request body".formatted(payload))
                            .fuzzer(this)
                            .payload(payload)
                            .build());
        }
    }

    @Override
    public String description() {
        return "send a request with dummy invalid json body";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }
}