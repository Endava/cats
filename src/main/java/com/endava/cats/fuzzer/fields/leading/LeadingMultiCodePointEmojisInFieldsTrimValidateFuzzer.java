package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
@EmojiFuzzer
@TrimAndValidate
public class LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values prefixed with multi code points emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadGenerator.getMultiCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}