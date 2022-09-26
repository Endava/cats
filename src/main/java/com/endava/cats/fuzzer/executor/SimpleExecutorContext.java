package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@Builder
@Value
public class SimpleExecutorContext {

    PrettyLogger logger;
    FuzzingData fuzzingData;

    String scenario;
    @NonNull
    ResponseCodeFamily expectedResponseCode;
    String expectedSpecificResponseCode;

    Fuzzer fuzzer;

    @Builder.Default
    boolean replaceRefData = true;

    @Builder.Default
    Predicate<HttpMethod> runFilter = httpMethod -> true;

    @Builder.Default
    Set<String> skippedHeaders = new HashSet<>();
    String payload;
    Collection<CatsHeader> headers;

    public String getExpectedSpecificResponseCode() {
        if (expectedSpecificResponseCode == null) {
            return expectedResponseCode.asString();
        }
        return expectedSpecificResponseCode;
    }

    /**
     * You can override the list of headers by populating the {@code headers} field.
     * This is useful when adding additional headers on top of the one supplied inside3 the API specs.
     *
     * @return a list of headers to be sent along with the request
     */
    public Collection<CatsHeader> getHeaders() {
        if (headers == null) {
            return fuzzingData.getHeaders();
        }
        return headers;
    }

    public String getPayload() {
        if (payload == null) {
            return fuzzingData.getPayload();
        }

        return payload;
    }
}
