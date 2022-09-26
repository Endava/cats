package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
    ResponseCodeFamily expectedResponseCodeForOptionalHeaders;
    Fuzzer fuzzer;

    Set<CatsHeader> headers;

    Supplier<List<FuzzingStrategy>> fuzzValueProducer;

    @Builder.Default
    boolean matchResponseSchema = true;

    public Set<CatsHeader> getHeaders() {
        if (headers == null) {
            return fuzzingData.getHeaders();
        }
        return headers;
    }
}
