package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;

import java.util.List;

/**
 * Abstract base class for fuzzers targeting invisible characters in base fields with trim and validation.
 * Extends the {@link ExpectOnly2XXBaseFieldsFuzzer} class and provides a constructor
 * to initialize common dependencies for fuzzing base fields with the expectation of only 2xx responses,
 * along with handling invisible characters, trimming, and validation.
 */
public abstract class InvisibleCharsBaseTrimValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing base fields with the expectation of only 2xx responses,
     * along with handling invisible characters, trimming, and validation.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    protected InvisibleCharsBaseTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return this.getInvisibleChars()
                .stream().map(value -> concreteFuzzStrategy().withData(value)).toList();
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return testCaseListener.isFieldNotADiscriminator(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send " + this.typeOfDataSentToTheService();
    }

    /**
     * Override to provide the list of invisible chars used for fuzzing.
     *
     * @return the list with invisible chars used for fuzzing
     */
    public abstract List<String> getInvisibleChars();

    /**
     * What is the actual fuzzing strategy to apply.
     *
     * @return the concrete fuzzing strategy to apply
     */
    public abstract FuzzingStrategy concreteFuzzStrategy();
}
