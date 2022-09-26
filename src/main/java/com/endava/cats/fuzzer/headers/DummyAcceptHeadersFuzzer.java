package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.google.common.net.HttpHeaders;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Singleton
@HeaderFuzzer
public class DummyAcceptHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {

    public DummyAcceptHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    public String getExpectedResponseCode() {
        return "406";
    }

    public String typeOfHeader() {
        return "dummy";
    }

    public String targetHeaderName() {
        return HttpHeaders.ACCEPT;
    }

    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
        clonedHeaders.add(CatsHeader.builder().name(HttpHeaders.ACCEPT).value(CATS_ACCEPT).build());

        return Collections.singletonList(clonedHeaders);
    }
}
