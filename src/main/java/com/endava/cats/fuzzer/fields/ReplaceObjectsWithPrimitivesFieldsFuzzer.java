package com.endava.cats.fuzzer.fields;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.CatsExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

@FieldFuzzer
@Singleton
public class ReplaceObjectsWithPrimitivesFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    protected final CatsUtil catsUtil;

    public ReplaceObjectsWithPrimitivesFieldsFuzzer(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> allFields = data.getAllFieldsByHttpMethod();
        logger.debug("All fields {}", allFields);

        for (String fuzzedField : allFields) {
            logger.trace("Checking if [{}] is object", fuzzedField);
            if (JsonUtils.isObject(data.getPayload(), fuzzedField)) {
                FuzzingStrategy replaceStrategy = FuzzingStrategy.replace().withData("cats_primitive_string");
                logger.debug("Field [{}] is object. Applying [{}]", fuzzedField, replaceStrategy);
                testCaseListener.createAndExecuteTest(logger, this, () -> process(data, fuzzedField, replaceStrategy));
            } else {
                logger.skip("Skipping primitive field [{}]. Fuzzer only runs for objects", fuzzedField);
            }
        }
    }

    protected void process(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        CatsExecutor.builder().scenario("Replace non-primitive fields with primitive values. Replacing [{}]")
                .fuzzingData(data).fuzzedField(fuzzedField).fuzzingStrategy(fuzzingStrategy)
                .expectedResponseCode(ResponseCodeFamily.FOURXX)
                .logger(logger).testCaseListener(testCaseListener).serviceCaller(serviceCaller).catsUtil(catsUtil)
                .build().execute();
    }

    @Override
    public String description() {
        return "iterate through each non-primitive field and replace it with primitive values";
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
