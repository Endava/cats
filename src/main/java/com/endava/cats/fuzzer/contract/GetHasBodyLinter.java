package com.endava.cats.fuzzer.contract;


import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractRequestWithoutBodyLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

@Singleton
@Linter
public class GetHasBodyLinter extends AbstractRequestWithoutBodyLinter {

    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
    protected GetHasBodyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    protected HttpMethod targetHttpMethod() {
        return HttpMethod.GET;
    }
}
