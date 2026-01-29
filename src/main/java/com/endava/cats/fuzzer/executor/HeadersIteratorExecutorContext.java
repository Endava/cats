package com.endava.cats.fuzzer.executor;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Context used by the HeadersIteratorExecutor.
 */
@Builder
@Value
public class HeadersIteratorExecutorContext {
    PrettyLogger logger;

    FuzzingData fuzzingData;

    String scenario;
    /**
     * If you provide an expected response code, it will be used and matched against what the service returns and report info, warn or error accordingly.
     * If not supplied, FieldsIteratorExecutor will test for Marching Arguments. If any match is found it will be reported as error, otherwise the test will be marked as skipped.
     */
    ResponseCodeFamily expectedResponseCodeForRequiredHeaders;
    /**
     * If you provide an expected response code, it will be used and matched against what the service returns and report info, warn or error accordingly.
     * If not supplied, FieldsIteratorExecutor will test for Marching Arguments. If any match is found it will be reported as error, otherwise the test will be marked as skipped.
     */
    Function<CatsHeader, ResponseCodeFamily> expectedResponseCodeForOptionalHeadersProducer;
    Fuzzer fuzzer;

    Supplier<List<FuzzingStrategy>> fuzzValueProducer;

    @Builder.Default
    boolean matchResponseSchema = true;

    @Builder.Default
    boolean skipAuthHeaders = true;

    @Builder.Default
    boolean shouldMatchContentType = true;
}
