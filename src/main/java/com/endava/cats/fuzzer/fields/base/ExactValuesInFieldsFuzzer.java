package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public abstract class ExactValuesInFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    protected ExactValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return String.format("exact %s size values", this.exactValueTypeString());
    }

    @Override
    public List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(StringSchema.class);
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        String pattern = schema.getPattern() != null ? schema.getPattern() : StringGenerator.ALPHANUMERIC;
        String generated = StringGenerator.generate(pattern, getExactMethod().apply(schema).intValue(), getExactMethod().apply(schema).intValue());
        if (schema instanceof ByteArraySchema) {
            return Base64.getEncoder().encodeToString(generated.getBytes(StandardCharsets.UTF_8));
        }
        return generated;
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        Map<String, String> refDataForCurrentPath = filesArguments.getRefData(data.getPath());
        return refDataForCurrentPath.isEmpty() && getExactMethod().apply(schema) != null;
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
        return String.format("iterate through each %s fields that have %s declared and send requests with values matching the %s size/value in the targeted field", getSchemasThatTheFuzzerWillApplyTo(), exactValueTypeString(), exactValueTypeString());
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