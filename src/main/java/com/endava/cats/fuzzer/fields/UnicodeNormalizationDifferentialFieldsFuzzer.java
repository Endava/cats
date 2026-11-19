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
 * Sends strings containing NFKC compatibility-decomposition length/byte bombs and
 * Windows/iconv Best-Fit syntax transformer codepoints across allocator and schema boundaries.
 */
@Singleton
@FieldFuzzer
public class UnicodeNormalizationDifferentialFieldsFuzzer extends TwoXXorFourXXReplaceStringsFuzzer {

    /**
     * Creates a new UnicodeNormalizationDifferentialFieldsFuzzer instance.
     *
     * @param testCaseListener       the test case listener
     * @param fieldsIteratorExecutor the fields iterator executor
     */
    public UnicodeNormalizationDifferentialFieldsFuzzer(TestCaseListener testCaseListener, FieldsIteratorExecutor fieldsIteratorExecutor) {
        super(testCaseListener, fieldsIteratorExecutor);
    }

    @Override
    protected BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer() {
        return (schema, _) -> UnicodeGenerator.generateNormalizationDifferentialPayloads(schema);
    }

    @Override
    protected String typesOfDataSentToTheService() {
        return "NFKC compatibility-expanding and Best-Fit syntax-transforming Unicode strings";
    }

    @Override
    public String description() {
        return "iterates through string fields and sends NFKC compatibility-expanding and Best-Fit syntax-transforming Unicode strings";
    }
}
