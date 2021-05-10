package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

//@Component
//@FieldFuzzer
@ConditionalOnProperty(value = "fuzzer.fields.ZeroWidthSpacesInStringFieldsFuzzer.enabled", havingValue = "true")
public class ZeroWidthSpacesInStringFieldsFuzzer extends BaseBoundaryFieldFuzzer {
    protected static final String ZERO_WIDTH_SPACE = "\u200B";

    @Autowired
    public ZeroWidthSpacesInStringFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "zero-width spaces in the middle of valid string values";
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(StringSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        String value = PayloadGenerator.generateValueBasedOnMinMAx(schema);
        int position = value.length() / 2;
        return value.substring(0, position) + ZERO_WIDTH_SPACE + value.substring(position + 1);
    }

    @Override
    protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return CollectionUtils.isEmpty(data.getRequestPropertyTypes().get(fuzzedField).getEnum());
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
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
    public String description() {
        return "iterate through each request fields and insert zero-width spaces in the middle of the targeted field valid values";
    }
}