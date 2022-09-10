package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.google.common.net.HttpHeaders;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Singleton
@HeaderFuzzer
public class UnsupportedAcceptHeadersFuzzer extends DummyAcceptHeadersFuzzer {

    public UnsupportedAcceptHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
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
