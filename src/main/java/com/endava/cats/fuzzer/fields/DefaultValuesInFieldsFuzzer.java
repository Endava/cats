package com.endava.cats.fuzzer.fields;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.CatsExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Set;

@Singleton
@FieldFuzzer
public class DefaultValuesInFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    protected final CatsUtil catsUtil;


    public DefaultValuesInFieldsFuzzer(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
    }

    public void fuzz(FuzzingData data) {
        Set<String> allFields = data.getAllFieldsByHttpMethod();
        logger.info("All fields {}", allFields);

        for (String fuzzedField : allFields) {
            Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
            if (fuzzedFieldSchema.getEnum() == null && testCaseListener.isFieldNotADiscriminator(fuzzedField) && fuzzedFieldSchema.getDefault() != null) {
                FuzzingStrategy replaceStrategy = FuzzingStrategy.replace().withData(fuzzedFieldSchema.getDefault());
                logger.debug("Field [{}] has a default value defined. Applying [{}]", fuzzedField, replaceStrategy);
                testCaseListener.createAndExecuteTest(logger, this, () -> CatsExecutor.builder()
                        .scenario("Iterate through each field with default value defined and send happy flow requests. Current field [{}]")
                        .fuzzingData(data).fuzzedField(fuzzedField).fuzzingStrategy(replaceStrategy)
                        .expectedResponseCode(ResponseCodeFamily.TWOXX)
                        .logger(logger).testCaseListener(testCaseListener).serviceCaller(serviceCaller).catsUtil(catsUtil)
                        .build().execute());
            } else {
                logger.skip("Skipping field [{}]. It does not have a defined default value.", fuzzedField);
            }
        }
    }

    @Override
    public String description() {
        return "iterate through each field with default values defined and send a happy flow request";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
