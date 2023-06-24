package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.BiFunction;

@Singleton
@FieldFuzzer
public class OverflowArraySizeFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    public OverflowArraySizeFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        BiFunction<Schema<?>, String, List<String>> fuzzValueProducer = (schema, string) -> {
            ArraySchema arraySchema = (ArraySchema) schema;
            int size = arraySchema.getMaxItems();
            String fieldValue = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), string + "[0]"));
            return List.of("[" + StringUtils.repeat(fieldValue, ",", size + 10) + "]");
        };

        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("array")
                .replaceWith("overflow array")
                .skipMessage("Fuzzer only runs for arrays")
                .fieldFilter(field -> JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer(fuzzValueProducer)
                .build();
    }
}