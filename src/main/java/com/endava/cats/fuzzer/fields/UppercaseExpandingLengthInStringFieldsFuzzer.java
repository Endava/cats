package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.TwoXXorFourXXReplaceStringsFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
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
        return (schema, s) -> {
            int schemaLength = schema.getMaxLength() != null ? schema.getMaxLength() : 10;
            return List.of(CatsUtil.selectRandom(UnicodeGenerator.getUppercaseExpandingLength(), schemaLength));
        };
    }

    @Override
    public String description() {
        return "iterate to string fields and send values that expand their length when uppercased";
    }
}
