package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * dummy content type
 */
@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.DummyContentTypeHeadersFuzzer.enabled", havingValue = "true")
public class DummyContentTypeHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {

    @Autowired
    public DummyContentTypeHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    public String getExpectedResponseCode() {
        return "415";
    }

    public String typeOfHeader() {
        return "dummy";
    }

    public String targetHeaderName() {
        return HttpHeaders.CONTENT_TYPE;
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
        clonedHeaders.add(CatsHeader.builder().name(HttpHeaders.CONTENT_TYPE).value(CATS_ACCEPT).build());

        return Collections.singletonList(clonedHeaders);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.DELETE, HttpMethod.GET);
    }
}
