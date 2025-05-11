package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseEnumIteratorFieldsFuzzer;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Fields that iterates through enum values and sends one request per each value.
 */
@Singleton
@FieldFuzzer
public class IterateThroughEnumValuesFieldsFuzzer extends BaseEnumIteratorFieldsFuzzer {

    /**
     * Creates a new IterateThroughEnumValuesFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public IterateThroughEnumValuesFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    protected BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer() {
        return (schema, field) -> schema.getEnum()
                .stream()
                .map(Object.class::cast)
                .toList();
    }

    @Override
    protected String scenario() {
        return "Iterate through each possible enum values and send happy flow requests.";
    }


    @Override
    public String description() {
        return "iterate through each enum field and send happy flow requests iterating through each possible enum values";
    }

}
