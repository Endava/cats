package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

/**
 * Base class for fuzzers sending only invisible chars in fields.
 */
public abstract class InvisibleCharsOnlyValidateTrimFuzzer extends InvisibleCharsOnlyTrimValidateFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing fields with invisible chars.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cu utility class
     * @param cp files arguments
     * @param fa filter arguments
     */
    protected InvisibleCharsOnlyValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, FilterArguments fa) {
        super(sc, lr, cu, cp, fa);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

}

