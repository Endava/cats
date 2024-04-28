package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that replaces JSON arrays with primitive values.
 */
@Singleton
@FieldFuzzer
public class ReplaceArraysWithPrimitivesFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    /**
     * Creates a new ReplaceArraysWithPrimitivesFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public ReplaceArraysWithPrimitivesFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("array")
                .replaceWith("primitive")
                .skipMessage("Fuzzer only runs for arrays")
                .fieldFilter(field -> JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of("cats_primitive_string"))
                .build();
    }
}
