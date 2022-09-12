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
public class IterateThroughEnumValuesFieldsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    protected final CatsUtil catsUtil;


    public IterateThroughEnumValuesFieldsFuzzer(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
    }

    public void fuzz(FuzzingData data) {
        Set<String> allFields = data.getAllFieldsByHttpMethod();
        logger.info("All fields {}", allFields);

        for (String fuzzedField : allFields) {
            Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
            if (fuzzedFieldSchema.getEnum() != null && testCaseListener.isFieldNotADiscriminator(fuzzedField)) {
                for (int i = 1; i < fuzzedFieldSchema.getEnum().size(); i++) {
                    FuzzingStrategy replaceStrategy = FuzzingStrategy.replace().withData(fuzzedFieldSchema.getEnum().get(i));
                    logger.debug("Field [{}] is an enum. Applying [{}]", fuzzedField, replaceStrategy);
                    testCaseListener.createAndExecuteTest(logger, this, () -> process(data, fuzzedField, replaceStrategy));
                }
            } else {
                logger.skip("Skipping field [{}]. It's either not an enum or it's a discriminator.", fuzzedField);
            }
        }
    }

    protected void process(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        CatsExecutor.builder().scenario("Iterate through each possible enum values and send happy flow requests. Current enum field [{}]")
                .fuzzingData(data).fuzzedField(fuzzedField).fuzzingStrategy(fuzzingStrategy)
                .expectedResponseCode(ResponseCodeFamily.TWOXX)
                .logger(logger).testCaseListener(testCaseListener).serviceCaller(serviceCaller).catsUtil(catsUtil)
                .build().execute();
    }

    @Override
    public String description() {
        return "iterate through each enum field and send happy flow requests iterating through each possible enum values";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
