package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that replaces JSON objects with primitive values.
 */
@FieldFuzzer
@Singleton
public class ReplaceObjectsWithPrimitivesFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    /**
     * Creates a new ReplaceObjectsWithPrimitivesFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public ReplaceObjectsWithPrimitivesFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("non-primitive")
                .replaceWith("primitive")
                .skipMessage("Fuzzer only runs for objects")
                .fieldFilter(field -> JsonUtils.isObject(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of("cats_primitive_string"))
                .build();
    }
}
