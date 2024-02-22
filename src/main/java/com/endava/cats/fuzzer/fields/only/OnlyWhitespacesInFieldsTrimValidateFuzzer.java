package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends only whitespaces in fields.
 */
@Singleton
@FieldFuzzer
@WhitespaceFuzzer
@TrimAndValidate
public class OnlyWhitespacesInFieldsTrimValidateFuzzer extends InvisibleCharsOnlyTrimValidateFuzzer {

    /**
     * Creates a new OnlyWhitespacesInFieldsTrimValidateFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param fa filter arguments
     */
    public OnlyWhitespacesInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, FilterArguments fa) {
        super(sc, lr, cp, fa);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values with unicode separators only";
    }

    @Override
    List<String> getInvisibleChars() {
        return UnicodeGenerator.getSeparatorsFields();
    }


}
