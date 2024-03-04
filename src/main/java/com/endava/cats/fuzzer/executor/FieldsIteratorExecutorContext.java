package com.endava.cats.fuzzer.executor;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Context used by the FieldsIteratorExecutor.
 */
@Builder
@Value
public class FieldsIteratorExecutorContext {

    PrettyLogger logger;

    FuzzingStrategy fuzzingStrategy;

    FuzzingData fuzzingData;

    String scenario;
    /**
     * If you provide an expected response code, it will be used and matched against what the service returns and report info, warn or error accordingly.
     * If not supplied, FieldsIteratorExecutor will test for Marching Arguments. If any match is found it will be reported as error, otherwise the test will be marked as skipped.
     */
    ResponseCodeFamily expectedResponseCode;

    Fuzzer fuzzer;
    /**
     * This is one of the filters used to decide if the Fuzzer logic will get executed.
     */
    @Builder.Default
    Predicate<Schema<?>> schemaFilter = schema -> true;
    /**
     * This is one of the filters used to decide if the Fuzzer logic will get executed.
     */
    @Builder.Default
    Predicate<String> fieldFilter = field -> true;

    String skipMessage;

    BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer;

    @Builder.Default
    boolean replaceRefData = true;

    /**
     * When this is true the executor will replace the field without any processing.
     */
    boolean simpleReplaceField;
}
