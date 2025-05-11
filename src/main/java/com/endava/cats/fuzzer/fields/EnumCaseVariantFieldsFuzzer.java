package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseEnumIteratorFieldsFuzzer;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

@Singleton
@FieldFuzzer
public class EnumCaseVariantFieldsFuzzer extends BaseEnumIteratorFieldsFuzzer {

    /**
     * Constructor for initializing the fuzzer with the provided executor.
     *
     * @param executor the executor used to perform the fuzzing
     */
    public EnumCaseVariantFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    protected BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer() {
        return (schema, field) -> schema.getEnum().stream()
                .map(Object::toString)
                .map(CatsUtil::randomizeCase)
                .filter(fuzzed -> !schema.getEnum().contains(fuzzed))
                .map(Object.class::cast)
                .toList();
    }

    @Override
    protected String scenario() {
        return "Iterate through each possible enum values and send random casing.";
    }

    @Override
    public String description() {
        return "iterate through each enum field and send case-variant values to test case sensitivity of enum handling";
    }
}