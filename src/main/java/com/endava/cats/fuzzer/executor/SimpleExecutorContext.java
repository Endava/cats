package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Builder
@Value
public class SimpleExecutorContext {

    /**
     * Fuzzer's logger.
     */
    PrettyLogger logger;
    @NonNull
    FuzzingData fuzzingData;

    String scenario;
    /**
     * This is used to match again the response code received from the Service.
     */
    ResponseCodeFamily expectedResponseCode;
    /**
     * You can supply this in order to be more specific in the expected response code section of the report. For example: {@code Should return 406}
     * If this is null, the report will consider {@code expectedResponseCode.asString()}. This will look like: {@code Should return 4XX}.
     */
    String expectedSpecificResponseCode;

    Fuzzer fuzzer;

    /**
     * Whether to replace ref data. Some Fuzzers will not want ref data to be replaced.
     */
    @Builder.Default
    boolean replaceRefData = true;

    /**
     * This filter will be used to decide if the test will be executed or not. If the Predicate is true, the test will be executed.
     */
    @Builder.Default
    Predicate<HttpMethod> runFilter = method -> true;

    /**
     * Any headers that will get removed before calling the service.
     */
    @Builder.Default
    Set<String> skippedHeaders = new HashSet<>();

    /**
     * A modified payload, other than the one from {@code FuzzingData#payload}. If this is null, the
     * Executor will call {@code fuzzingData.getPayload()}.
     */
    String payload;

    /**
     * You can override the list of headers by populating the {@code headers} field.
     * This is useful when adding additional headers on top of the one supplied inside3 the API specs.
     */
    Collection<CatsHeader> headers;

    /**
     * A custom response processor. The Executor only calls {@code TestCaseListener#reportResult}. If more complex logic is needed
     * to process the response and decide the expected behaviours, you can supply your own processor.
     */
    BiConsumer<CatsResponse, FuzzingData> responseProcessor;

    /**
     * Whether to add the headers supplied in the {@code --headers} file.
     */
    @Builder.Default
    boolean addUserHeaders = true;

    /**
     * If populated, this will get appended after the "Should return XXX" text.
     */
    @Builder.Default
    String expectedResult = "";

    HttpMethod httpMethod;

    /**
     * If {@code httpMethod} is null, it will return the {@code fuzzingData.getHttpMethod()}.
     *
     * @return the http method to be executed
     */
    public HttpMethod getHttpMethod() {
        if (httpMethod == null) {
            return fuzzingData.getMethod();
        }
        return httpMethod;
    }

    /**
     * If the {@code expectedSpecificResponseCode} is null, this method will return {@code expectedResponseCode.asString()}.
     *
     * @return the expected response code to match against
     */
    public String getExpectedSpecificResponseCode() {
        if (expectedSpecificResponseCode == null) {
            return expectedResponseCode.asString();
        }
        return expectedSpecificResponseCode;
    }

    /**
     * If {@code headers} is null, it will return {@code fuzzingData.getHeaders()}.
     *
     * @return a list of headers to be sent along with the request
     */
    public Collection<CatsHeader> getHeaders() {
        if (headers == null) {
            return fuzzingData.getHeaders();
        }
        return headers;
    }

    /**
     * A modified payload, other than the one from {@code FuzzingData#payload}. If this is null, the
     * Executor will call {@code fuzzingData.getPayload()}.
     *
     * @return the payload to be sent to the service
     */
    public String getPayload() {
        if (payload == null) {
            return fuzzingData.getPayload();
        }

        return payload;
    }
}
