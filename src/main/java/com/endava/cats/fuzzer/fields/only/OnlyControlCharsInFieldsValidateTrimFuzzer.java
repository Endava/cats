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

@Singleton
@FieldFuzzer
@ControlCharFuzzer
@ValidateAndTrim
public class OnlyControlCharsInFieldsValidateTrimFuzzer extends InvisibleCharsOnlyValidateTrimFuzzer {

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
