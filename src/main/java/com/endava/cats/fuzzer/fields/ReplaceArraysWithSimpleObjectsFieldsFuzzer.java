package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that replace JSON arrays with simple objects.
 */
@FieldFuzzer
@Singleton
public class ReplaceArraysWithSimpleObjectsFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    /**
     * Creates a new ReplaceArraysWithSimpleObjectsFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public ReplaceArraysWithSimpleObjectsFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("array")
                .replaceWith("simple object")
                .skipMessage("Fuzzer only runs for arrays")
                .fieldFilter(field -> JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of("{\"catsKey1\":\"catsValue1\",\"catsKey2\":20}"))
                .build();
    }
}
