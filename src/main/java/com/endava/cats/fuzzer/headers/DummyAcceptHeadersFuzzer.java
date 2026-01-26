package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.HttpHeaders;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Sends dummy Accept headers.
 */
@Singleton
@HeaderFuzzer
public class DummyAcceptHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     */
    public DummyAcceptHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    @Override
    public ResponseCodeFamily getResponseCodeFamily() {
        return ResponseCodeFamilyPredefined.FOURXX_MT;
    }

    @Override
    public String getExpectedResponseCode() {
        return "406";
    }

    @Override
    public String typeOfHeader() {
        return "dummy";
    }

    @Override
    public String targetHeaderName() {
        return HttpHeaders.ACCEPT;
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
        clonedHeaders.add(CatsHeader.builder().name(HttpHeaders.ACCEPT).value(CATS_ACCEPT).build());

        return Collections.singletonList(clonedHeaders);
    }
}
