package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.HttpHeaders;
import com.endava.cats.util.JsonUtils;
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
    public ResponseCodeFamily getResponseCodeFamily(FuzzingData data) {
        if (data != null && data.getMethod() != null && !HttpMethod.requiresBody(data.getMethod())) {
            return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
        }
        return this.getResponseCodeFamily();
    }

    @Override
    public String getExpectedResponseCode(FuzzingData data) {
        if (data != null && data.getMethod() != null && !HttpMethod.requiresBody(data.getMethod())) {
            return "2XX or 4XX";
        }
        return this.getExpectedResponseCode();
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        if (data.getPayload() == null) {
            return Collections.emptyList();
        }
        Set<CatsHeader> dummyHeader = Cloner.cloneMe(data.getHeaders());
        dummyHeader.add(CatsHeader.builder().name(HttpHeaders.CONTENT_TYPE).value(CATS_ACCEPT).build());

        Set<CatsHeader> malformedMultipartHeader = Cloner.cloneMe(data.getHeaders());
        malformedMultipartHeader.add(CatsHeader.builder().name(HttpHeaders.CONTENT_TYPE).value("multipart/form-data").build());

        if (data.getMethod() != null && !HttpMethod.requiresBody(data.getMethod())) {
            return List.of(dummyHeader, malformedMultipartHeader);
        }

        Set<CatsHeader> unsupportedCharsetHeader = Cloner.cloneMe(data.getHeaders());
        unsupportedCharsetHeader.add(CatsHeader.builder().name(HttpHeaders.CONTENT_TYPE).value("application/json; charset=UTF-7").build());

        return List.of(dummyHeader, malformedMultipartHeader, unsupportedCharsetHeader);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
