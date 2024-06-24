package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract base class for fuzzers targeting exact values in fields.
 * Extends the {@link BaseBoundaryFieldFuzzer} class and provides a constructor
 * to initialize common dependencies for fuzzing exact values in fields.
 */
public abstract class ExactValuesInFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing exact values in fields.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    protected ExactValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return String.format("exact %s size values", this.exactValueTypeString());
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    /**
     * This method will generate a boundary value based on the supplied schema.
     * There are 2 cases when this method gets involved (see {@link BaseBoundaryFieldFuzzer#isFieldFuzzable(String, FuzzingData)}):
     * <ol>
     *     <li>When the schema has left or right boundaries defined</li>
     *     <li>When the field has a defined format and the format is recognizable</li>
     * </ol>
     * <p>
     * The method will generate a boundary value only if there is a left or right boundary defined.
     * It won't generate values if the format is recognized, but it doesn't have any boundaries defined,
     * as those scenarios are already covered by the HappyPathFuzzer.
     *
     * @param schema used to extract boundary information
     * @return null of the schema has proper boundaries defined or a generated string value matching the boundaries otherwise
     */
    @Override
    public Object getBoundaryValue(Schema schema) {
        Number fromSchemaLength = getExactMethod().apply(schema);
        if (fromSchemaLength == null) {
            logger.debug("Null value for applied boundary function!");
            return null;
        }
        logger.debug("Length from schema {}", fromSchemaLength);
        /* Sometimes the regex generators will generate weird chars at the beginning or end of string.
          So we generate a larger one and substring the right size. */
        try {
            return generateWithAdjustedLength(schema, 15);
        } catch (IllegalArgumentException e) {
            try {
                return generateWithAdjustedLength(schema, 0);
            } catch (Exception ex) {
                testCaseListener.recordError("Fuzzer %s could not generate a value for patten %s".formatted(this.getClass().getSimpleName(), schema.getPattern()));
                return null;
            }
        }
    }

    private String generateWithAdjustedLength(Schema schema, int adjustedLength) {
        Number fromSchemaLength = getExactMethod().apply(schema);
        String pattern = schema.getPattern() != null ? schema.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;

        int fromSchemaLengthAdjusted = (fromSchemaLength.intValue() > Integer.MAX_VALUE / 100 - adjustedLength) ? Integer.MAX_VALUE / 100 : fromSchemaLength.intValue();
        int generatedStringLength = fromSchemaLengthAdjusted + adjustedLength;

        String generated = StringGenerator.generateExactLength(pattern, generatedStringLength);
        if (CatsModelUtils.isByteArraySchema(schema)) {
            return Base64.getEncoder().encodeToString(generated.getBytes(StandardCharsets.UTF_8));
        }
        logger.debug("Generated value: {}, fromSchemaAdjusted: {}", generated, fromSchemaLengthAdjusted);
        return generated.substring(0, fromSchemaLengthAdjusted);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        boolean isRefDataField = filesArguments.getRefData(data.getPath()).get(fuzzedField) != null;
        return !isRefDataField && getExactMethod().apply(schema) != null && StringUtils.isBlank(schema.getFormat());
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.TWOXX;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return String.format("iterate through each %s fields that have %s defined and send values matching the %s size/value",
                getSchemaTypesTheFuzzerWillApplyTo(), exactValueTypeString(), exactValueTypeString());
    }

    /**
     * Either minimum or maximum String.
     *
     * @return minimum or maximum string
     */
    protected abstract String exactValueTypeString();

    /**
     * The exact Function used to generate the exact values. This is either getMinimum, getMaximum, getMinLength or getMaxLength.
     *
     * @return the function to apply to get the exact value
     */
    protected abstract Function<Schema, Number> getExactMethod();
}