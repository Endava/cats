package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Fuzzer that sends examples from components.examples; from schema.example(s), MediaType.example(s) Example can be a ref.
 */
@FieldFuzzer
@Singleton
public class ExamplesFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ExamplesFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new ExamplesFieldsFuzzer instance.
     *
     * @param simpleExecutor the executor
     */
    @Inject
    public ExamplesFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> payloads = new HashSet<>();
        Predicate<Object> removeCatsComposition = example -> !example.toString().contains("_OF");
        payloads.add(Optional.ofNullable(data.getReqSchema().getExample())
                .filter(removeCatsComposition).map(JsonUtils.GSON::toJson).orElse(""));
        payloads.addAll(
                Optional.ofNullable(data.getReqSchema().getExamples())
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(removeCatsComposition)
                        .map(JsonUtils.GSON::toJson)
                        .toList());
        payloads.addAll(data.getExamples().stream().filter(removeCatsComposition).toList());
        payloads.remove("");

        for (String payload : payloads) {
            simpleExecutor.execute(SimpleExecutorContext
                    .builder()
                    .payload(payload)
                    .logger(logger)
                    .fuzzer(this)
                    .fuzzingData(data)
                    .scenario("Send a request for every unique example")
                    .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
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
