package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

@Linter
@Singleton
public class PatchWithoutBodyLinter extends AbstractRequestBodyLinter {
    protected PatchWithoutBodyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    protected HttpMethod targetHttpMethod() {
        return HttpMethod.PATCH;
    }
}
