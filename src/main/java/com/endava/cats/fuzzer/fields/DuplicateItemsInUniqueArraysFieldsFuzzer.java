package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Duplicates an item in arrays declared with {@code uniqueItems: true}.
 *
 * <p>This bean must be a singleton because {@link BaseReplaceFieldsFuzzer} has no no-args constructor
 * and therefore cannot be proxied using {@code ApplicationScoped}.</p>
 */
@Singleton
@FieldFuzzer
public class DuplicateItemsInUniqueArraysFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    /**
     * Creates a new DuplicateItemsInUniqueArraysFieldsFuzzer instance.
     *
     * @param executor the executor used to iterate through array fields
     */
    public DuplicateItemsInUniqueArraysFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("arrays with uniqueItems enabled")
                .replaceWith("arrays containing a duplicate item")
                .skipMessage("Fuzzer only runs for non-empty arrays with uniqueItems enabled")
                .fieldFilter(field -> canDuplicateItem(data, field))
                .fuzzValueProducer((schema, field) -> duplicateItem(data.getPayload(), field))
                .build();
    }

    private static boolean canDuplicateItem(FuzzingData data, String field) {
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        if (schema == null || !Boolean.TRUE.equals(schema.getUniqueItems())) {
            return false;
        }

        Optional<JsonArray> array = getArray(data.getPayload(), field);
        if (array.isEmpty() || array.get().isEmpty()) {
            return false;
        }

        return array.get().size() > 1 || schema.getMaxItems() == null || schema.getMaxItems() > 1;
    }

    private static List<Object> duplicateItem(String payload, String field) {
        return getArray(payload, field)
                .map(DuplicateItemsInUniqueArraysFieldsFuzzer::duplicateFirstItem)
                .<List<Object>>map(array -> List.of(array.toString()))
                .orElseGet(List::of);
    }

    private static JsonArray duplicateFirstItem(JsonArray original) {
        JsonArray result = original.deepCopy();
        JsonElement duplicate = original.get(0).deepCopy();
        if (result.size() == 1) {
            result.add(duplicate);
        } else {
            result.set(result.size() - 1, duplicate);
        }
        return result;
    }

    private static Optional<JsonArray> getArray(String payload, String field) {
        if (!JsonUtils.isArray(payload, field)) {
            return Optional.empty();
        }

        String serialized = JsonUtils.serialize(JsonUtils.getVariableFromJson(payload, field));
        if (serialized == null) {
            return Optional.empty();
        }

        JsonElement element = JsonParser.parseString(serialized);
        return element.isJsonArray() ? Optional.of(element.getAsJsonArray()) : Optional.empty();
    }
}
