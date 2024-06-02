package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

@Singleton
@FieldFuzzer
public class ZeroWidthCharsInNamesFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ZeroWidthCharsInNamesFieldsFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new ZeroWidthCharsInNamesFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     */
    public ZeroWidthCharsInNamesFieldsFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skip fuzzer as payload is empty");
            return;
        }
        for (String fuzzValue : UnicodeGenerator.getZwCharsSmallListFields()) {
            for (String fuzzedField : data.getAllFieldsByHttpMethod().stream().limit(5).collect(Collectors.toSet())) {
                testCaseListener.createAndExecuteTest(logger, this, () -> process(data, fuzzedField, fuzzValue));
            }
        }
    }

    private void process(FuzzingData data, String fuzzedField, String fuzzValue) {
        String fuzzedPayload = getFuzzedPayload(data, fuzzedField, fuzzValue);

        testCaseListener.addScenario(logger, "Insert zero-width chars in field names: field [{}], char [{}]. All other details are similar to a happy flow", fuzzedField, fuzzValue);
        testCaseListener.addExpectedResult(logger, "Should get a [{}] response code", ResponseCodeFamilyPredefined.FOURXX.asString());

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedPayload).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).contractPath(data.getContractPath())
                .contentType(data.getFirstRequestContentType()).pathParamsPayload(data.getPathParamsPayload())
                .build());
        testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
    }

    private static @NotNull String getFuzzedPayload(FuzzingData data, String fuzzedField, String fuzzValue) {
        String currentPayload = data.getPayload();
        String simpleFuzzedFieldName = fuzzedField.substring(fuzzedField.lastIndexOf("#") + 1);
        String fuzzedFieldName = CatsUtil.insertInTheMiddle(simpleFuzzedFieldName, fuzzValue, true);
        return currentPayload.replace("\"" + simpleFuzzedFieldName + "\"", "\"" + fuzzedFieldName + "\"");
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterate through each field and insert zero-width characters in the field names";
    }
}
