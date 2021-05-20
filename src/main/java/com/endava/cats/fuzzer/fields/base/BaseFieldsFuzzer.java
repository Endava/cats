package com.endava.cats.fuzzer.fields.base;


import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.*;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class performs the actual fuzzing. It can be extended to provide expected result codes based on different fuzzing scenarios.
 */
public abstract class BaseFieldsFuzzer implements Fuzzer {
    public static final String CATS_REMOVE_FIELD = "cats_remove_field";
    protected final CatsUtil catsUtil;
    final FilesArguments filesArguments;
    final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;


    @Autowired
    protected BaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
        this.filesArguments = cp;
    }


    @Override
    public void fuzz(FuzzingData data) {
        logger.info("All required fields, including subfields: {}", data.getAllRequiredFields());
        logger.info("All fields {}", data.getAllFields());

        List<String> fieldsToBeRemoved = filesArguments.getRefData(data.getPath()).keySet()
                .stream().filter(CATS_REMOVE_FIELD::equalsIgnoreCase).collect(Collectors.toList());
        logger.note("The following fields marked as [{}] in refData will not be fuzzed: {}", CATS_REMOVE_FIELD, fieldsToBeRemoved);

        Set<String> allFields = data.getAllFields();
        fieldsToBeRemoved.forEach(allFields::remove);

        for (String fuzzedField : data.getAllFields()) {
            for (FuzzingStrategy fuzzingStrategy : this.getFieldFuzzingStrategy(data, fuzzedField)) {
                testCaseListener.createAndExecuteTest(logger, this, () -> process(data, fuzzedField, fuzzingStrategy));
            }
        }
    }

    protected void process(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        FuzzingConstraints fuzzingConstraints = this.createFuzzingConstraints(data, fuzzingStrategy, fuzzedField);

        testCaseListener.addScenario(logger, "Send [{}] in request fields: field [{}], value [{}], is required [{}]",
                this.typeOfDataSentToTheService(), fuzzedField, fuzzingStrategy.truncatedValue(), fuzzingConstraints.getRequiredString());

        if (this.isFuzzingPossible(data, fuzzedField, fuzzingStrategy)) {
            FuzzingResult fuzzingResult = catsUtil.replaceField(data.getPayload(), fuzzedField, fuzzingStrategy);
            boolean isFuzzedValueMatchingPattern = this.isFuzzedValueMatchingPattern(fuzzingResult.getFuzzedValue(), data, fuzzedField);
            
            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath())
                    .headers(data.getHeaders()).payload(fuzzingResult.getJson()).httpMethod(data.getMethod())
                    .fuzzedField(fuzzedField).queryParams(data.getQueryParams()).build();

            CatsResponse response = serviceCaller.call(serviceData);

            ResponseCodeFamily expectedResponseCodeBasedOnConstraints = this.getExpectedResponseCodeBasedOnConstraints(isFuzzedValueMatchingPattern, fuzzingConstraints);

            testCaseListener.addExpectedResult(logger, "Should return [{}]", expectedResponseCodeBasedOnConstraints.asString());
            testCaseListener.reportResult(logger, data, response, expectedResponseCodeBasedOnConstraints);
        } else {
            FuzzingStrategy strategy = this.createSkipStrategy(fuzzingStrategy);
            testCaseListener.skipTest(logger, strategy.process(""));
        }
    }

    private FuzzingStrategy createSkipStrategy(FuzzingStrategy fuzzingStrategy) {
        return fuzzingStrategy.isSkip() ? fuzzingStrategy : FuzzingStrategy.skip().withData("Field is not a primitive or is a discriminator");
    }

    private boolean isFuzzingPossible(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return !fuzzingStrategy.isSkip() && catsUtil.isPrimitive(data.getPayload(), fuzzedField) && isFuzzingPossibleSpecificToFuzzer(data, fuzzedField, fuzzingStrategy);
    }

    private ResponseCodeFamily getExpectedResponseCodeBasedOnConstraints(boolean isFuzzedValueMatchingPattern, FuzzingConstraints fuzzingConstraints) {
        if (!isFuzzedValueMatchingPattern) {
            return this.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern();
        }
        return this.getResultCodeBasedOnMandatoryFieldsFuzzed(fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed());
    }

    private FuzzingConstraints createFuzzingConstraints(FuzzingData data, FuzzingStrategy strategy, String fuzzedField) {
        boolean hasMinLength = this.hasMinValue(data, fuzzedField) && strategy.getData() != null;
        boolean hasRequiredFieldsFuzzed = data.getAllRequiredFields().contains(fuzzedField);

        return FuzzingConstraints.builder().hasMinlength(hasMinLength)
                .hasRequiredFieldsFuzzed(hasRequiredFieldsFuzzed).build();
    }

    private boolean isFuzzedValueMatchingPattern(String fieldValue, FuzzingData data, String fuzzedField) {
        Schema fieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        if (fieldSchema.getPattern() == null) {
            return true;
        }
        Pattern pattern = Pattern.compile(fieldSchema.getPattern());

        return fieldValue == null || pattern.matcher(fieldValue).matches();
    }

    private boolean hasMinValue(FuzzingData data, String fuzzedField) {
        Schema fieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        return fieldSchema != null && fieldSchema.getMinLength() != null && fieldSchema.getMinLength() > 0;
    }

    private ResponseCodeFamily getResultCodeBasedOnMandatoryFieldsFuzzed(boolean mandatoryFieldsFuzzed) {
        return mandatoryFieldsFuzzed ? this.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() : this.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed();
    }


    /**
     * A simple description of the current data being sent to the service. This will be used as a description in the final report.
     *
     * @return type of data to be sent to the service
     */
    protected abstract String typeOfDataSentToTheService();

    /**
     * What is the expected HTTP code when a required field is fuzzed with an invalid value
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed();

    /**
     * What is the expected HTTP code when an optional field is fuzzed with an invalid value
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed();

    /**
     * What is the expected HTTP code when the fuzzed value does not match the supplied Schema pattern (if defined)
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern();

    /**
     * What is the fuzzing strategy for the implementing Fuzzer
     *
     * @param data        contains all details related to the current contract path
     * @param fuzzedField the name of the current field being fuzzed
     * @return a list of FuzzingStrategies to be applied by the Fuzzer
     */
    protected abstract List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField);

    /**
     * Override this in order to prevent the fuzzer from running for context particular to the given fuzzer.
     *
     * @param data
     * @param fuzzedField
     * @param fuzzingStrategy
     * @return
     */
    protected boolean isFuzzingPossibleSpecificToFuzzer(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return true;
    }
}