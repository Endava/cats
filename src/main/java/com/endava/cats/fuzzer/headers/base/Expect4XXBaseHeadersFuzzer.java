package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamily;

public abstract class Expect4XXBaseHeadersFuzzer extends BaseHeadersFuzzer {

    protected Expect4XXBaseHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }
}
