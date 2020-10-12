package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;

public abstract class Expect4XXBaseHeadersFuzzer extends BaseHeadersFuzzer {

    protected Expect4XXBaseHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
