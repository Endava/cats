package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.json.JsonUtils;
import com.google.common.net.HttpHeaders;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * a list with valid, but not supported
 */
@Singleton
@HeaderFuzzer
public class UnsupportedContentTypesHeadersFuzzer extends DummyContentTypeHeadersFuzzer {

    public UnsupportedContentTypesHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    @Override
    public String typeOfHeader() {
        return "unsupported";
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            return Collections.emptyList();
        }
        return filterHeaders(data, HttpHeaders.CONTENT_TYPE, data.getRequestContentTypes());
    }

}
