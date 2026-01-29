package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;

/**
 * Sends empty string in all HTTP headers declared in the OpenAPI spec.
 */
@Singleton
@HeaderFuzzer
public class EmptyStringsInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new instance.
     *
     * @param headersIteratorExecutor executor used to run the fuzz logic
     */
    public EmptyStringsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersProducer(header -> {
                    boolean shouldBe4xx = !header.isRequired() && StringUtils.isNotBlank(header.getFormat());
                    return shouldBe4xx ? ResponseCodeFamilyPredefined.FOURXX_TWOXX : ResponseCodeFamilyPredefined.TWOXX;
                })
                .typeOfDataSentToTheService("empty values")
                .fuzzStrategy(Collections.singletonList(FuzzingStrategy.replace().withData("")))
                .build();
    }

}
