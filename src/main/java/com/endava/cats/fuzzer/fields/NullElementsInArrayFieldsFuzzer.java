package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

/**
 * Fuzzer that replaces JSON arrays with a single-element array containing {@code null} ({@code [null]})
 * to verify element-level null handling when iterating over collections.
 */
@Singleton
@FieldFuzzer
public class NullElementsInArrayFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    /**
     * Creates a new NullElementsInArrayFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public NullElementsInArrayFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("array")
                .replaceWith("array containing null element")
                .skipMessage("Fuzzer only runs for arrays")
                .fieldFilter(field -> JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of(Collections.singletonList(null)))
                .build();
    }
}
