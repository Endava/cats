package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import lombok.Builder;

@Builder
public class CatsExecutor {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private CatsUtil catsUtil;
    private PrettyLogger logger;
    private FuzzingStrategy fuzzingStrategy;
    private String fuzzedField;
    private FuzzingData fuzzingData;
    private String scenario;
    private ResponseCodeFamily expectedResponseCode;


    public void execute() {
        testCaseListener.addScenario(logger, scenario, fuzzedField);
        testCaseListener.addExpectedResult(logger, "Should return [{}]", expectedResponseCode.asString());
        FuzzingResult fuzzingResult = catsUtil.replaceField(fuzzingData.getPayload(), fuzzedField, fuzzingStrategy);

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(fuzzingData.getPath()).headers(fuzzingData.getHeaders())
                .payload(fuzzingResult.getJson()).queryParams(fuzzingData.getQueryParams()).httpMethod(fuzzingData.getMethod())
                .contentType(fuzzingData.getFirstRequestContentType()).build());
        testCaseListener.reportResult(logger, fuzzingData, response, expectedResponseCode);
    }
}
