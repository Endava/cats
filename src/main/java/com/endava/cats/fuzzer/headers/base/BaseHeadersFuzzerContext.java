package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Holds context information for headers fuzzing.
 */
@Getter
@Builder
public class BaseHeadersFuzzerContext {
    /**
     * Short description of data that is sent to the service.
     */
    private final String typeOfDataSentToTheService;
    /**
     * What is the expected HTTP Code when required headers are fuzzed with invalid values
     */
    private final ResponseCodeFamily expectedHttpCodeForRequiredHeadersFuzzed;
    /**
     * What is the expected HTTP code when optional headers are fuzzed with invalid values.
     */
    private final ResponseCodeFamily expectedHttpForOptionalHeadersFuzzed;
    /**
     * What is the Fuzzing strategy the current Fuzzer will apply.
     */
    private final List<FuzzingStrategy> fuzzStrategy;

    /**
     * There is a special case when we send Control Chars in Headers and an error (due to HTTP RFC specs)
     * is returned by the app server itself, not the application. In this case we don't want to check
     * if there is even a response body as the error page/response is served by the server, not the application layer.
     */
    @Builder.Default
    private final boolean matchResponseSchema = true;
}
