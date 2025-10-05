package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This fuzzer inserts leading zeros in numeric field values.
 */
//@Singleton
//@FieldFuzzer
public class PrefixNumbersWithZeroFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PrefixNumbersWithZeroFieldsFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new PrefixNumbersWithZeroFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     */
    public PrefixNumbersWithZeroFieldsFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skip fuzzer as payload is empty");
            return;
        }
        for (String fuzzedField : data.getAllFieldsByHttpMethod()
                .stream()
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .filter(field -> {
                    Schema<?> fuzzedValueSchema = data.getRequestPropertyTypes().get(field);
                    return CatsModelUtils.isNumberSchema(fuzzedValueSchema) || CatsModelUtils.isIntegerSchema(fuzzedValueSchema);
                })
                .collect(Collectors.toSet())) {
            testCaseListener.createAndExecuteTest(logger, this, () -> process(data, fuzzedField), data);
        }
    }

    private void process(FuzzingData data, String fuzzedField) {
        String fuzzValue = "00" + CatsUtil.random().nextInt();
        String fuzzedPayload = CatsUtil.justReplaceField(data.getPayload(), fuzzedField, "MODIFIED")
                .json()
                .replace("\"MODIFIED\"", fuzzValue);

        testCaseListener.addScenario(logger, "Insert leading zero in numeric field values: field [{}], char [{}]",
                fuzzedField, fuzzValue);
        testCaseListener.addExpectedResult(logger, "Should get a [{}] response code", ResponseCodeFamilyPredefined.FOURXX.asString());

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedPayload).queryParams(data.getQueryParams()).httpMethod(data.getMethod())
                .contractPath(data.getContractPath()).contentType(data.getFirstRequestContentType()).pathParamsPayload(data.getPathParamsPayload()).build());

        testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterate through each numeric field and prefix values with zeros";
    }
}