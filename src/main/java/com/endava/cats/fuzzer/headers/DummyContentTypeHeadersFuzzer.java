package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.google.common.net.HttpHeaders;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Sends dummy Content-Type headers.
 */
@Singleton
@HeaderFuzzer
public class DummyContentTypeHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     */
    public DummyContentTypeHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    @Override
    public String getExpectedResponseCode() {
        return "415";
    }

    @Override
    public String typeOfHeader() {
        return "dummy";
    }

    @Override
    public String targetHeaderName() {
        return HttpHeaders.CONTENT_TYPE;
    }

    @Override
    public ResponseCodeFamily getResponseCodeFamily() {
        return ResponseCodeFamilyPredefined.FOURXX_MT;
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            return Collections.emptyList();
        }
        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
        clonedHeaders.add(CatsHeader.builder().name(HttpHeaders.CONTENT_TYPE).value(CATS_ACCEPT).build());

        return Collections.singletonList(clonedHeaders);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.DELETE, HttpMethod.GET);
    }
}
