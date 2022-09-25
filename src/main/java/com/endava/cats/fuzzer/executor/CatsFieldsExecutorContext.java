package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Builder
@Value
public class CatsFieldsExecutorContext {

    PrettyLogger logger;
    FuzzingStrategy fuzzingStrategy;
    FuzzingData fuzzingData;
    String scenario;
    /**
     * If you provide an expected response code, it will be used and matched against what the service returns and report info, warn or error accordingly.
     * If not supplied, CatsFieldsExecutor will test for Marching Arguments. If any match is found it will be reported as error, otherwise the test will be marked as skipped.
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
    Function<Schema<?>, List<String>> fuzzValueProducer;
}
