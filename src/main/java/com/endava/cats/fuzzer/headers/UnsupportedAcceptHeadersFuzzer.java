package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.UnsupportedAcceptHeadersFuzzer.enabled", havingValue = "true")
public class UnsupportedAcceptHeadersFuzzer extends DummyAcceptHeadersFuzzer {

    @Autowired
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
                stream().flatMap(Collection::stream).collect(Collectors.toList()));
    }
}
