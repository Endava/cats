package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.ControlCharFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.TrimAndValidate;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
@ControlCharFuzzer
@TrimAndValidate
public class TrailingControlCharsInFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected TrailingControlCharsInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values trailed with unicode control characters";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getControlCharsFields();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.trail();
    }
}