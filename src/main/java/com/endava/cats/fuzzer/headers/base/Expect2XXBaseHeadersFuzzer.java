package com.endava.cats.fuzzer.headers.base;


import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;

public abstract class Expect2XXBaseHeadersFuzzer extends BaseHeadersFuzzer {

    protected Expect2XXBaseHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
