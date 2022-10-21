package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * // from components.examples; from schema.example(s), MediaType.example(s) Example can be ref
 */
@FieldFuzzer
@Singleton
public class ExamplesFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ExamplesFieldsFuzzer.class);
    private final CatsGlobalContext catsGlobalContext;
    private final SimpleExecutor simpleExecutor;

    @Inject
    public ExamplesFieldsFuzzer(CatsGlobalContext catsGlobalContext, SimpleExecutor simpleExecutor) {
        this.catsGlobalContext = catsGlobalContext;
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> payloads = new HashSet<>();
        payloads.add(Optional.ofNullable(data.getReqSchema().getExample()).map(JsonUtils.GSON::toJson).orElse(""));
        Optional.ofNullable(data.getReqSchema().getExamples()).orElse(List.of()).forEach(payload -> payloads.add(JsonUtils.GSON.toJson(payload)));
        payloads.addAll(data.getExamples());
        payloads.remove("");

        for (String payload : payloads) {
            simpleExecutor.execute(SimpleExecutorContext
                    .builder()
                    .payload(payload)
                    .logger(logger)
                    .fuzzer(this)
                    .fuzzingData(data)
                    .scenario("Send a request for every unique example")
                    .expectedResponseCode(ResponseCodeFamily.TWOXX)
                    .expectedSpecificResponseCode("2XX")
                    .build()
            );
        }
    }

    @Override
    public String description() {
        return "send a request for every unique example";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
