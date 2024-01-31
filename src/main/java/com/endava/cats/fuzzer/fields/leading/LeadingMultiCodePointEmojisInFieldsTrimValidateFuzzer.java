package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer prefixing valid field values with multi code point emojis.
 */
@Singleton
@FieldFuzzer
@EmojiFuzzer
@TrimAndValidate
public class LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    /**
     * Creates a new LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer instance.
     *
     * @param sc The {@link ServiceCaller} used to make service calls
     * @param lr The {@link TestCaseListener} for reporting test case events
     * @param cu The {@link CatsUtil} for utility functions related to CATS
     * @param cp The {@link FilesArguments} for file-related arguments
     */
    protected LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values prefixed with multi code points emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getMultiCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}