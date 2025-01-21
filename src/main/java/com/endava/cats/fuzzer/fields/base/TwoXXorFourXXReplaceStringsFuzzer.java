package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Base class for fuzzing fields by replacing them with values that are expect to return 2XX or 4XX response codes.
 */
public abstract class TwoXXorFourXXReplaceStringsFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());

    protected final TestCaseListener testCaseListener;
    protected final FieldsIteratorExecutor fieldsIteratorExecutor;

    /**
     * Constructor for initializing common dependencies for fuzzing fields.
     *
     * @param testCaseListener       the test case listener
     * @param fieldsIteratorExecutor the executor
     */
    @Inject
    protected TwoXXorFourXXReplaceStringsFuzzer(TestCaseListener testCaseListener, FieldsIteratorExecutor fieldsIteratorExecutor) {
        this.testCaseListener = testCaseListener;
        this.fieldsIteratorExecutor = fieldsIteratorExecutor;
    }

    public void fuzz(FuzzingData data) {
        ResponseCodeFamily expectedResponseCodes = new ResponseCodeFamilyDynamic(List.of("2XX", "4XX"));
        fieldsIteratorExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Replace fields with characters %s".formatted(this.typesOfDataSentToTheService()))
                        .fuzzingData(data).fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(expectedResponseCodes)
                        .fuzzValueProducer(this.fuzzValueProducer())
                        .fieldFilter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                        .schemaFilter(CatsModelUtils::isStringSchema)
                        .skipMessage("Field is not a string.")
                        .simpleReplaceField(true)
                        .logger(logger)
                        .fuzzer(this)
                        .build());
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    /**
     * Override to provide the values used to fuzz.
     *
     * @return a BiFunction that produces the values used to fuzz
     */
    protected abstract BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer();

    /**
     * Override to provide the types of data sent to the service.
     *
     * @return a String representing the types of data sent to the service
     */
    protected abstract String typesOfDataSentToTheService();
}
