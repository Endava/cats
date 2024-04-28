package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Fuzzer that overflows maps.
 */
@FieldFuzzer
@Singleton
public class OverflowMapSizeFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new OverflowMapSizeFieldsFuzzer instance.
     *
     * @param ce the executor
     * @param pa to get the size of the overflow size
     */
    public OverflowMapSizeFieldsFuzzer(FieldsIteratorExecutor ce, ProcessingArguments pa) {
        super(ce);
        this.processingArguments = pa;
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer = (schema, field) -> {
            Object allMapKeys = JsonUtils.getVariableFromJson(data.getPayload(), field + ".keys()");
            String firstKey = allMapKeys instanceof String s ? s : ((Set<String>) allMapKeys).iterator().next();
            Object firstKeyValue = JsonUtils.getVariableFromJson(data.getPayload(), field + "." + firstKey);

            Map<String, Object> finalResult = new HashMap<>();
            int arraySize = schema.getMaxProperties() != null ? schema.getMaxProperties() + 10 : processingArguments.getLargeStringsSize();
            for (int i = 0; i < arraySize; i++) {
                finalResult.put(firstKey + i, firstKeyValue);
            }
            return List.of(JsonUtils.GSON.toJson(finalResult));
        };

        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("dictionary/hashmap")
                .replaceWith("overflow dictionary/hashmap")
                .skipMessage("Fuzzer only runs for dictionaries/hashmaps")
                .fieldFilter(field -> data.getRequestPropertyTypes().get(field).getAdditionalProperties() != null
                        && JsonUtils.isValidMap(data.getPayload(), field))
                .fuzzValueProducer(fuzzValueProducer)
                .build();
    }
}