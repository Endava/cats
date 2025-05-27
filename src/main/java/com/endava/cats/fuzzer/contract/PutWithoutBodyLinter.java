package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractRequestBodyLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

@Linter
@Singleton
public class PutWithoutBodyLinter extends AbstractRequestBodyLinter {

    public PutWithoutBodyLinter(TestCaseListener testCaseListener) {
        super(testCaseListener);
    }

    @Override
    protected HttpMethod targetHttpMethod() {
        return HttpMethod.PUT;
    }
}
