package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class ExactValuesInFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    protected ExactValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return String.format("exact %s size values", this.exactValueTypeString());
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(StringSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        String pattern = schema.getPattern() != null ? schema.getPattern() : StringGenerator.ALPHANUMERIC;
        return StringGenerator.generate(pattern, getExactMethod().apply(schema).intValue(), getExactMethod().apply(schema).intValue());
    }

    @Override
    protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        Map<String, String> currentPath = catsParams.getRefData(data.getPath());
        return currentPath.isEmpty() && getExactMethod().apply(schema) != null;
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return String.format("iterate through each %s fields that have %s declared and send requests with values matching the %s size/value in the targeted field", getSchemasThatTheFuzzerWillApplyTo(), exactValueTypeString(), exactValueTypeString());
    }

    protected abstract String exactValueTypeString();

    protected abstract Function<Schema, Number> getExactMethod();
}