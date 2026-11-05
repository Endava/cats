package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.Expect4XXForRequiredBaseFieldsFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingConstraints;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends empty values in string fields.
 */
@Singleton
@FieldFuzzer
public class EmptyStringsInFieldsFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {
    private final FilterArguments filterArguments;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new EmptyStringsInFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param fa filter arguments
     */
    public EmptyStringsInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, FilterArguments fa) {
        this(sc, lr, cp, fa, null);
    }

    /**
     * Creates a new EmptyStringsInFieldsFuzzer instance with processing arguments.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param fa filter arguments
     * @param pa processing arguments
     */
    @Inject
    public EmptyStringsInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, FilterArguments fa, ProcessingArguments pa) {
        super(sc, lr, cp);
        this.filterArguments = fa;
        this.processingArguments = pa;
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "empty strings";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return List.of(FuzzingStrategy.replace().withData(""));
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    protected ResponseCodeFamily getExpectedResponseCodeBasedOnConstraints(FuzzingData data, String fuzzedField, boolean isFuzzedValueMatchingPattern, FuzzingConstraints fuzzingConstraints) {
        if (data.isQueryParam(fuzzedField) && !fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed()) {
            return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
        }
        Schema<?> fieldSchema = data.getRequestPropertyTypes() != null ? data.getRequestPropertyTypes().get(fuzzedField) : null;
        if (CatsModelUtils.isBooleanSchema(fieldSchema) && isLenientTypes()) {
            return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
        }
        if (isNonEmptyEnumWithoutEmptyString(fieldSchema)) {
            return fieldSchema.getDefault() != null && !fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed()
                    ? ResponseCodeFamilyPredefined.TWOXX
                    : ResponseCodeFamilyPredefined.FOURXX;
        }
        return super.getExpectedResponseCodeBasedOnConstraints(data, fuzzedField, isFuzzedValueMatchingPattern, fuzzingConstraints);
    }

    private boolean isLenientTypes() {
        return processingArguments != null && !processingArguments.isStrictTypes();
    }

    private static boolean isNonEmptyEnumWithoutEmptyString(Schema<?> fieldSchema) {
        return fieldSchema != null
                && fieldSchema.getEnum() != null
                && !fieldSchema.getEnum().isEmpty()
                && !fieldSchema.getEnum().contains("");
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return HttpMethod.requiresBody(data.getMethod()) || data.isQueryParam(fuzzedField);
    }

    @Override
    public List<String> skipForFields() {
        return filterArguments.getSkipFields();
    }

    @Override
    public String description() {
        return "iterate through each field and send empty String values";
    }
}
