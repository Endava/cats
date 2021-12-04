package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.apache.http.HttpHeaders;

import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * a list with valid, but not supported
 */
@Singleton
@HeaderFuzzer
public class UnsupportedContentTypesHeadersFuzzer extends DummyContentTypeHeadersFuzzer {

    public UnsupportedContentTypesHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfHeader() {
        return "unsupported";
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        return filterHeaders(data, HttpHeaders.CONTENT_TYPE, data.getRequestContentTypes());
    }

}
