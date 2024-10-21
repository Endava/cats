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
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Set;

@FieldFuzzer
@Singleton
public class InsertWhitespacesInFieldNamesFieldFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(NewFieldsFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new InsertWhitespacesInFieldNamesFieldFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     */
    public InsertWhitespacesInFieldNamesFieldFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skipping fuzzer for path {} because the payload is empty", data.getPath());
            return;
        }

        Set<String> allFieldsByHttpMethod = data.getAllFieldsByHttpMethod();
        String randomWhitespace = UnicodeGenerator.generateRandomUnicodeString(2, Character::isWhitespace);

        for (String field : allFieldsByHttpMethod) {
            logger.debug("Fuzzing field {}, inserting {}", field, randomWhitespace);
            if (JsonUtils.isFieldInJson(data.getPayload(), field)) {
                testCaseListener.createAndExecuteTest(logger, this, () -> process(data, field, randomWhitespace), data);
            }
        }
    }

    private void process(FuzzingData data, String field, String randomWhitespace) {
        testCaseListener.addScenario(logger, "Insert random whitespaces in the field name [{}]", field);
        testCaseListener.addExpectedResult(logger, "Should get a [{}] response code", ResponseCodeFamilyPredefined.FOURXX);

        String fuzzedJson = JsonUtils.insertCharactersInFieldKey(data.getPayload(), field, randomWhitespace);

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(fuzzedJson).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).contractPath(data.getContractPath())
                .contentType(data.getFirstRequestContentType()).pathParamsPayload(data.getPathParamsPayload())
                .build());
        testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
    }


    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterates through each request field name and insert random whitespaces";
    }
}