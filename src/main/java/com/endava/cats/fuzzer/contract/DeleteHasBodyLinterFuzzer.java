package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

@LinterFuzzer
@Singleton
public class DeleteHasBodyLinterFuzzer extends AbstractRequestWithoutBodyLinterFuzzer {
    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
    protected DeleteHasBodyLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    protected HttpMethod targetHttpMethod() {
        return HttpMethod.DELETE;
    }
}
