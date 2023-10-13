package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.google.common.net.HttpHeaders;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Singleton
@HeaderFuzzer
public class DummyTransferEncodingHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {
    private static final String DUMMY_TRANSFER = "cats";

    public DummyTransferEncodingHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    @Override
    public String getExpectedResponseCode() {
        return "400|501";
    }

    @Override
    public String typeOfHeader() {
        return "dummy";
    }

    @Override
    public ResponseCodeFamily getResponseCodeFamily() {
        return ResponseCodeFamily.FOUR00_FIVE01;
    }

    @Override
    public String targetHeaderName() {
        return HttpHeaders.TRANSFER_ENCODING;
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
        clonedHeaders.add(CatsHeader.builder().name(HttpHeaders.TRANSFER_ENCODING).value(DUMMY_TRANSFER).build());

        return Collections.singletonList(clonedHeaders);
    }
}