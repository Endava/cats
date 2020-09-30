package com.endava.cats.fuzzer.headers;

import com.endava.cats.generator.Cloner;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class DummyAcceptHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {

    @Autowired
    public DummyAcceptHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    public String getExpectedResponseCode() {
        return "406";
    }

    public String typeOfHeader() {
        return "dummy";
    }

    public String targetHeaderName() {
        return "Accept";
    }

    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
        clonedHeaders.add(CatsHeader.builder().name(HttpHeaders.ACCEPT).value(CATS_ACCEPT).build());

        return Collections.singletonList(clonedHeaders);
    }
}
