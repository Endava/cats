package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
@EmojiFuzzer
@ValidateAndTrim
public class OnlyMultiCodePointEmojisInFieldsValidateTrimFuzzer extends InvisibleCharsOnlyValidateTrimFuzzer {

    public OnlyMultiCodePointEmojisInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, IgnoreArguments fa) {
        super(sc, lr, cu, cp, fa);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values with multi code point emojis only";
    }

    @Override
    List<String> getInvisibleChars() {
        return PayloadGenerator.getMultiCodePointEmojis();
    }
}