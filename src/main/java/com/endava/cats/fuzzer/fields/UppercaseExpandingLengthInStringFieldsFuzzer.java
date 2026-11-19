package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.TwoXXorFourXXReplaceStringsFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

/**
 * The fuzzer that sends values that will expand their length when uppercased.
 */
@Singleton
@FieldFuzzer
public class UppercaseExpandingLengthInStringFieldsFuzzer extends TwoXXorFourXXReplaceStringsFuzzer {
    /**
     * Constructor for initializing common dependencies for fuzzing fields.
     *
     * @param testCaseListener       the test case listener
     * @param fieldsIteratorExecutor the executor
     */
    public UppercaseExpandingLengthInStringFieldsFuzzer(TestCaseListener testCaseListener, FieldsIteratorExecutor fieldsIteratorExecutor) {
        super(testCaseListener, fieldsIteratorExecutor);
    }

    @Override
    protected String typesOfDataSentToTheService() {
        return "values that will expand their length when uppercased";
    }


    @Override
    protected BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer() {
        return (schema, _) -> UnicodeGenerator.generateCaseExpandingPayloads(schema, UnicodeGenerator.getUppercaseExpandingLength());
    }

    @Override
    public String description() {
        return "iterate to string fields and send values that expand their length when uppercased";
    }
}
