package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends very large unicode strings in string fields. Size of the large unicode
 * strings is controlled by the {@code --largeStringsSize} argument.
 */
@Singleton
@FieldFuzzer
public class VeryLargeUnicodeStringsInFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    private final ProcessingArguments processingArguments;

    /**
     * Creates a new VeryLargeUnicodeStringsInFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param pa to get the size of the large strings
     */
    public VeryLargeUnicodeStringsInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large unicode values";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.getLargeValuesStrategy(processingArguments.getLargeStringsSize());
    }

    @Override
    protected boolean shouldMatchContentType() {
        return false;
    }

    @Override
    protected boolean shouldCheckForFuzzedValueMatchingPattern() {
        return false;
    }

    @Override
    public String description() {
        return "iterate through each field and send very large random unicode values";
    }
}