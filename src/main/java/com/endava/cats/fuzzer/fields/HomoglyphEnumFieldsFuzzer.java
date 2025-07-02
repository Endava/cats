package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Replaces characters inside enum values with Unicode homoglyphs
 * (e.g., LATIN 'A' → GREEK ALPHA) to expose Trojan-Source-style
 * validation or authorisation bypasses.
 */
@FieldFuzzer
@Singleton
public class HomoglyphEnumFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing fields.
     *
     * @param ce the executor
     */
    public HomoglyphEnumFieldsFuzzer(FieldsIteratorExecutor ce) {
        super(ce);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        BiFunction<Schema<?>, String, List<Object>> producer = (schema, field) -> {
            String original = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), field));
            List<Object> results = new ArrayList<>();

            for (int idx = 0; idx < original.length(); idx++) {
                char ch = original.charAt(idx);
                if (UnicodeGenerator.getHomoglyphs().containsKey(ch)) {
                    char homo = UnicodeGenerator.getHomoglyphs().get(ch);
                    String mutated = original.substring(0, idx) + homo + original.substring(idx + 1);
                    if (!mutated.equals(original)) {
                        results.add(mutated);
                    }
                }
            }
            return results.isEmpty() ? List.of(original) : results;
        };

        return BaseReplaceFieldsContext.builder()
                .replaceWhat("enum value")
                .replaceWith("homoglyph-altered value")
                .skipMessage("Fuzzer only runs for string enums")
                .fieldFilter(field -> {
                    Schema<?> schema = data.getRequestPropertyTypes().get(field);
                    return CatsModelUtils.isEnumSchema(schema) && JsonUtils.isFieldInJson(data.getPayload(), field);
                })
                .fuzzValueProducer(producer)
                .build();
    }
}