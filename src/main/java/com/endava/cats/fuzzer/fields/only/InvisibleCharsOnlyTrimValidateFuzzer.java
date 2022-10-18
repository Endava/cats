package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.fuzzer.fields.base.Expect4XXForRequiredBaseFieldsFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;

import java.util.List;

public abstract class InvisibleCharsOnlyTrimValidateFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {
    private final IgnoreArguments ignoreArguments;

    protected InvisibleCharsOnlyTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, IgnoreArguments fa) {
        super(sc, lr, cu, cp);
        this.ignoreArguments = fa;
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return this.getInvisibleChars().stream()
                .map(value -> FuzzingStrategy.getFuzzStrategyWithRepeatedCharacterReplacingValidValue(data, fuzzedField, value)).toList();
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
        return ignoreArguments.getSkipFields();
    }

    @Override
    public String description() {
        return "iterate through each field and send  " + this.typeOfDataSentToTheService();
    }

    abstract List<String> getInvisibleChars();
}