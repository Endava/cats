package com.endava.cats.fuzzer.fields.base;


import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingConstraints;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.FuzzingResult;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class performs the actual fuzzing. It can be extended to provide expected result codes based on different fuzzing scenarios.
 */
public abstract class BaseFieldsFuzzer implements Fuzzer {
    public static final String CATS_REMOVE_FIELD = "cats_remove_field";
    protected final CatsUtil catsUtil;
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    protected final TestCaseListener testCaseListener;
    protected final FilesArguments filesArguments;
    private final ServiceCaller serviceCaller;

    protected BaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
        this.filesArguments = cp;
    }


    @Override
    public void fuzz(FuzzingData data) {
        Set<String> allFields = data.getAllFieldsByHttpMethod();
        logger.debug("All required fields, including subfields: {}", data.getAllRequiredFields());
        logger.debug("All fields {}", allFields);

        List<String> fieldsToBeRemoved = filesArguments.getRefData(data.getPath()).entrySet()
                .stream().filter(entry -> entry.getValue().equalsIgnoreCase(CATS_REMOVE_FIELD)).map(Map.Entry::getKey).toList();
        logger.note("The following fields marked as [{}] in refData will not be fuzzed: {}", CATS_REMOVE_FIELD, fieldsToBeRemoved);

        fieldsToBeRemoved.forEach(allFields::remove);

        if (allFields.isEmpty()) {
            logger.skip("Skipped due to: no fields to fuzz!");
        } else {
            for (String fuzzedField : allFields) {
                for (FuzzingStrategy fuzzingStrategy : this.getFieldFuzzingStrategy(data, fuzzedField)) {
                    logger.debug("Running strategy {} for {}", fuzzingStrategy.name(), fuzzedField);
                    testCaseListener.createAndExecuteTest(logger, this, () -> process(data, fuzzedField, fuzzingStrategy));
                }
            }
        }
    }

    protected void process(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        FuzzingConstraints fuzzingConstraints = this.createFuzzingConstraints(data, fuzzingStrategy, fuzzedField);

        testCaseListener.addScenario(logger, "Send [{}] in request fields: field [{}], value [{}], is required [{}]",
                this.typeOfDataSentToTheService(), fuzzedField, fuzzingStrategy.truncatedValue(), fuzzingConstraints.getRequiredString());

        if (this.isFuzzingPossible(data, fuzzedField, fuzzingStrategy)) {
            logger.debug("Fuzzing possible...");
            FuzzingResult fuzzingResult = catsUtil.replaceField(data.getPayload(), fuzzedField, fuzzingStrategy);
            boolean isFuzzedValueMatchingPattern = this.isFuzzedValueMatchingPattern(fuzzingResult.fuzzedValue(), data, fuzzedField);

            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath())
                    .headers(data.getHeaders()).payload(fuzzingResult.json()).httpMethod(data.getMethod()).contractPath(data.getContractPath())
                    .fuzzedField(fuzzedField).queryParams(data.getQueryParams()).contentType(data.getFirstRequestContentType()).build();

            CatsResponse response = serviceCaller.call(serviceData);

            ResponseCodeFamily expectedResponseCodeBasedOnConstraints = this.getExpectedResponseCodeBasedOnConstraints(isFuzzedValueMatchingPattern, fuzzingConstraints);

            testCaseListener.addExpectedResult(logger, "Should return [{}]", expectedResponseCodeBasedOnConstraints.asString());
            testCaseListener.reportResult(logger, data, response, expectedResponseCodeBasedOnConstraints);
        } else {
            logger.debug("Fuzzing not possible!");
            FuzzingStrategy strategy = this.createSkipStrategy(fuzzingStrategy);
            testCaseListener.skipTest(logger, (String) strategy.process(""));
        }
    }

    private FuzzingStrategy createSkipStrategy(FuzzingStrategy fuzzingStrategy) {
        return fuzzingStrategy.isSkip() ? fuzzingStrategy : FuzzingStrategy.skip().withData("Field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator or is not matching the Fuzzer schemas");
    }

    /**
     * Fuzzing is not always possible. We will skip fuzzing if:
     * <ol>
     *     <li>the field is not a JSON primitive</li>
     *     <li>the field is not marked as skipped</li>
     *     <li>FuzzingStrategy is marked as skipped. This might happen if there is no boundary defined for the field, the Fuzzer cannot be applied to the current fuzzedField type or the String format is not recognized. </li>
     *     <li>each Fuzzer can have additional logic to skip its execution</li>
     * </ol>
     *
     * @param data            the current FuzzingData object
     * @param fuzzedField     the current fuzzed field
     * @param fuzzingStrategy the current FuzzingStrategy return by the current fuzzer
     * @return true if fuzzing is possible, false otherwise
     */
    private boolean isFuzzingPossible(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return !fuzzingStrategy.isSkip() && JsonUtils.isPrimitive(data.getPayload(), fuzzedField)
                && isFuzzerWillingToFuzz(data, fuzzedField)
                && !isSkippedField(fuzzedField);
    }

    private boolean isSkippedField(String fuzzedField) {
        return skipForFields().contains(fuzzedField);
    }

    /**
     * Compute the expected response code based on various constraints. Each specific Fuzzer will override the methods to return what is expected when the following criteria are met:
     * <ol>
     *     <li>what is the expected response code when the fuzzed value is not matching the pattern defined inside the contract</li>
     *     <li>is the fuzzed field mandatory or not</li>
     * </ol>
     *
     * @param isFuzzedValueMatchingPattern is the fuzzed value matching the pattern defined in the contract?
     * @param fuzzingConstraints           fuzzing constraints associated to the current fuzzed field
     * @return the appropriate ResponseCodeFamily based on the supplied conditions
     */
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

    /**
     * For byte format OpenAPI is expecting a base64 encoded string. We consider this matching any pattern.
     *
     * @param fieldValue  the current value of the field
     * @param data        the current FuzzingData object
     * @param fuzzedField the name of the field being fuzzed
     * @return true if the fuzzed value matches the pattern, false otherwise
     */
    private boolean isFuzzedValueMatchingPattern(Object fieldValue, FuzzingData data, String fuzzedField) {
        Schema<?> fieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        if (fieldSchema.getPattern() == null || fieldSchema instanceof ByteArraySchema) {
            return true;
        }
        Pattern pattern = Pattern.compile(fieldSchema.getPattern());

        return fieldValue == null || pattern.matcher(this.sanitizeString(fieldValue)).matches();
    }

    /**
     * We need to sanitize the fuzzed value before matching it to the pattern as APIs are expected to
     * also sanitize data before validating it.
     *
     * @param fieldValue the initial fuzzed value
     * @return the initial value with unicode control chars removed
     */
    private String sanitizeString(Object fieldValue) {
        return String.valueOf(fieldValue).replaceAll("\\p{C}", "");
    }

    private boolean hasMinValue(FuzzingData data, String fuzzedField) {
        Schema<?> fieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
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
     * @param data        the current FuzzingData object
     * @param fuzzedField the current field being fuzzed
     * @return true by default
     */
    protected boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return true;
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}