package com.endava.cats.fuzzer.fields.leading;


import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.TrimAndValidate;
import com.endava.cats.annotations.WhitespaceFuzzer;
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
 * Fuzzer prefixing valid field values with whitespaces.
 */
@Singleton
@FieldFuzzer
@WhitespaceFuzzer
@TrimAndValidate
public class LeadingWhitespacesInFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    /**
     * Creates a new LeadingWhitespacesInFieldsTrimValidateFuzzer instance.
     *
     * @param sc The {@link ServiceCaller} used to make service calls
     * @param lr The {@link TestCaseListener} for reporting test case events
     * @param cu The {@link CatsUtil} for utility functions related to CATS
     * @param cp The {@link FilesArguments} for file-related arguments
     */
    protected LeadingWhitespacesInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "values prefixed with unicode separators";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getSeparatorsFields();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}