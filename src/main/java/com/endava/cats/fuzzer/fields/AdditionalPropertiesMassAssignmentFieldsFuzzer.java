package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Adds unexpected properties to objects declared with additionalProperties:false
 * to detect Mass-Assignment / over-posting vulnerabilities.
 */
@FieldFuzzer
@Singleton
public class AdditionalPropertiesMassAssignmentFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    private static final String FUZZY_KEY_PREFIX = "catsExtraProp";
    private static final int EXTRA_KEYS_COUNT = 100;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new AdditionalPropertiesMassAssignmentFieldsFuzzer instance.
     *
     * @param ce the executor
     * @param pa to get the size of the overflow size
     */
    public AdditionalPropertiesMassAssignmentFieldsFuzzer(FieldsIteratorExecutor ce, ProcessingArguments pa) {
        super(ce);
        this.processingArguments = pa;
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer = (schema, field) -> {
            Map<String, Object> mutated = new HashMap<>();

            Object keysObj = JsonUtils.getVariableFromJson(data.getPayload(), field + ".keys()");
            if (keysObj instanceof String key) {
                mutated.put(key, JsonUtils.getVariableFromJson(data.getPayload(), field + "." + key));
            } else if (keysObj instanceof Set<?> keys) {
                for (Object k : keys) {
                    String key = String.valueOf(k);
                    mutated.put(key, JsonUtils.getVariableFromJson(data.getPayload(), field + "." + key));
                }
            }

            Object sampleValue = mutated.values().stream()
                    .findAny()
                    .orElse("catsFuzzyValue");

            int extra = Math.min(EXTRA_KEYS_COUNT,
                    processingArguments.getLargeStringsSize() / 4);
            for (int i = 0; i < extra; i++) {
                mutated.put(FUZZY_KEY_PREFIX + i, sampleValue);
            }

            return List.of(JsonUtils.GSON.toJson(mutated));
        };

        return BaseReplaceFieldsContext.builder()
                .replaceWhat("object without additional properties")
                .replaceWith("object containing unexpected extra properties")
                .skipMessage("Fuzzer only runs for objects with additionalProperties:false")
                .fieldFilter(field -> {
                    Schema<?> schema = data.getRequestPropertyTypes().get(field);
                    boolean forbidsExtras = schema != null &&
                            Boolean.FALSE.equals(schema.getAdditionalProperties());
                    return forbidsExtras && JsonUtils.isValidMap(data.getPayload(), field);
                })
                .fuzzValueProducer(fuzzValueProducer)
                .build();
    }
}