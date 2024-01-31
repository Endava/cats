package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

/**
 * Abstract base class for fuzzers expecting only 2xx responses for base fields.
 * Extends the {@link BaseFieldsFuzzer} class and provides a constructor
 * to initialize common dependencies for fuzzing base fields with the expectation of only 2xx responses.
 */
public abstract class ExpectOnly2XXBaseFieldsFuzzer extends BaseFieldsFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing base fields with the expectation of only 2xx responses.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cu The {@link CatsUtil} for utility functions related to CATS (Compliance and Testing Suite).
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    protected ExpectOnly2XXBaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.TWOXX;
    }
}
