package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

/** Exercises Base64 decoder tails at block boundaries using a bounded set of valid and malformed values. */
@Singleton
@FieldFuzzer
public class Base64DecoderBoundaryFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(Base64DecoderBoundaryFieldsFuzzer.class);
    private final FieldsIteratorExecutor executor;

    public Base64DecoderBoundaryFieldsFuzzer(FieldsIteratorExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        executor.execute(FieldsIteratorExecutorContext.builder()
                .scenario("Send valid and malformed Base64 tails around 64-, 128-, and 256-character decoder boundaries.")
                .fuzzingData(data).logger(logger).fuzzer(this).fuzzingStrategy(FuzzingStrategy.replace())
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                .fieldFilter(field -> isTarget(data, field))
                .fuzzValueProducer((_, _) -> Base64DecoderInputs.boundaryValues())
                .simpleReplaceField(true)
                .skipMessage("Field is not a present, non-enum Base64 string or recognized cursor field.")
                .build());
    }

    private static boolean isTarget(FuzzingData data, String field) {
        return Base64DecoderInputs.appliesTo(data.getRequestPropertyTypes().get(field), field)
                && JsonUtils.isFieldInJson(data.getPayload(), field);
    }

    @Override
    public String description() {
        return "checks declared Base64 strings and cursor fields with bounded decoder-length and padding variations";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
