package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Singleton;

import java.util.List;

@FieldFuzzer
@Singleton
public class ReplaceObjectsWithArraysFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    public ReplaceObjectsWithArraysFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("object")
                .replaceWith("array")
                .skipMessage("Fuzzer only runs for objects")
                .fieldFilter(field -> JsonUtils.isObject(data.getPayload(), field) && !JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of("[{\"catsKey1\":\"catsValue1\",\"catsKey2\":20},{\"catsKey3\":\"catsValue3\",\"catsKey3\":40}]"))
                .build();
    }
}
