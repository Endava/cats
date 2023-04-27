package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.google.common.net.HttpHeaders;

import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Singleton
@HeaderFuzzer
public class UnsupportedAcceptHeadersFuzzer extends DummyAcceptHeadersFuzzer {

    public UnsupportedAcceptHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    @Override
    public String typeOfHeader() {
        return "unsupported";
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        return filterHeaders(data, HttpHeaders.ACCEPT, data.getResponseContentTypes().values().
                stream().flatMap(Collection::stream).toList());
    }
}
