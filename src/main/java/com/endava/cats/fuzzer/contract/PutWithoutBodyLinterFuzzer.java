package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

@LinterFuzzer
@Singleton
public class PutWithoutBodyLinterFuzzer extends AbstractRequestBodyLinterFuzzer {

    public PutWithoutBodyLinterFuzzer(TestCaseListener testCaseListener) {
        super(testCaseListener);
    }

    @Override
    protected HttpMethod targetHttpMethod() {
        return HttpMethod.PUT;
    }
}
