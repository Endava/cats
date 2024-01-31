package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends only control chars in fields.
 */
@Singleton
@FieldFuzzer
@ControlCharFuzzer
@ValidateAndTrim
public class OnlyControlCharsInFieldsValidateTrimFuzzer extends InvisibleCharsOnlyValidateTrimFuzzer {

    /**
     * Creates a new OnlyControlCharsInFieldsValidateTrimFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cu utility class
     * @param cp files arguments
     * @param fa filter arguments
     */
    public OnlyControlCharsInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, FilterArguments fa) {
        super(sc, lr, cu, cp, fa);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values with control chars only";
    }

    @Override
    List<String> getInvisibleChars() {
        return UnicodeGenerator.getControlCharsFields();
    }

}
