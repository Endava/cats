package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamily;

public abstract class ExpectOnly4XXBaseHeadersFuzzer extends BaseHeadersFuzzer {
    protected ExpectOnly4XXBaseHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }
}
