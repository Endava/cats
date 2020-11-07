package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "edgeSpacesStrategy", havingValue = "trimAndValidate", matchIfMissing = true)
public class SpacesOnlyInFieldsTrimValidateFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {

    @Autowired
    public SpacesOnlyInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
        super(sc, lr, cu, cp);
    }

    static FuzzingStrategy getFuzzStrategy(FuzzingData data, String fuzzedField) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        String spaceValue = "  ";
        if (schema != null && schema.getMinLength() != null) {
            spaceValue = StringUtils.repeat(spaceValue, schema.getMinLength() + 1);
        }
        return FuzzingStrategy.replace().withData(spaceValue);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "spaces only";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return getFuzzStrategy(data, fuzzedField);
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with spaces in the targeted field";
    }
}
