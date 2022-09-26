package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamily;

public abstract class SpacesCharsBaseFuzzer extends InvisibleCharsBaseFuzzer {

    protected SpacesCharsBaseFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }
}
