package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

public abstract class ExactValuesInFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    protected ExactValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
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
    public String getBoundaryValue(Schema schema) {
        if (getExactMethod().apply(schema) == null) {
            logger.debug("Null value for applied boundary function!");
            return null;
        }
        String pattern = schema.getPattern() != null ? schema.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;

        /* Sometimes the regex generators will generate weird chars at the beginning or end of string.
          So we generate a larger one and substring the right size. */
        int fromSchemaLength = getExactMethod().apply(schema).intValue();
        int generatedStringLength = fromSchemaLength + 15;

        String generated = StringGenerator.generate(pattern, generatedStringLength, generatedStringLength);
        if (schema instanceof ByteArraySchema) {
            return Base64.getEncoder().encodeToString(generated.getBytes(StandardCharsets.UTF_8));
        }

        return StringGenerator.sanitize(generated).substring(5, fromSchemaLength + 5);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        boolean isRefDataField = filesArguments.getRefData(data.getPath()).get(fuzzedField) != null;
        return !isRefDataField && getExactMethod().apply(schema) != null && StringUtils.isBlank(schema.getFormat());
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.TWOXX;
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