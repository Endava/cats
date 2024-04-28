package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that replaces JSON primitive fields with a dummy JSON object.
 */
@FieldFuzzer
@Singleton
public class ReplacePrimitivesWithObjectsFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    /**
     * Creates a new ReplacePrimitivesWithObjectsFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public ReplacePrimitivesWithObjectsFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("primitive")
                .replaceWith("object")
                .skipMessage("Fuzzer only runs for primitives")
                .fieldFilter(field -> JsonUtils.isPrimitive(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of("{\"catsKey1\":\"catsValue1\",\"catsKey2\":20}"))
                .build();
    }
}