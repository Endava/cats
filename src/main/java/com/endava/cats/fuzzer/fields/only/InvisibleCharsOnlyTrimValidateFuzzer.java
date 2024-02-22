package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.fields.base.Expect4XXForRequiredBaseFieldsFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;

/**
 * Base class for fuzzers sending only invisible chars in fields.
 */
public abstract class InvisibleCharsOnlyTrimValidateFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {
    private final FilterArguments filterArguments;

    /**
     * Constructor for initializing common dependencies for fuzzing fields with invisible chars.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param fa filter arguments
     */
    protected InvisibleCharsOnlyTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, FilterArguments fa) {
        super(sc, lr, cp);
        this.filterArguments = fa;
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);
        return this.getInvisibleChars().stream()
                .map(value -> FuzzingStrategy.getFuzzStrategyWithRepeatedCharacterReplacingValidValue(schema, value)).toList();
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    /**
     * Supplied skipped fields are skipped when we only sent invalid data.
     *
     * @return the list with skipped fields.
     */
    @Override
    public List<String> skipForFields() {
        return filterArguments.getSkipFields();
    }

    @Override
    public String description() {
        return "iterate through each field and send  " + this.typeOfDataSentToTheService();
    }

    /**
     * Returns the actual list of invisible chars to be used for fuzzing.
     *
     * @return a list of invisible chars to be used for fuzzing.
     */
    abstract List<String> getInvisibleChars();
}