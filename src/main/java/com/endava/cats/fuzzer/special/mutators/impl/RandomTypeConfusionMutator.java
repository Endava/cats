package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Replaces the target field with unexpected JSON structural types (empty/null-containing arrays,
 * empty/operator/null-id objects, and booleans) to trigger deserialization and ORM type-confusion errors.
 */
@Singleton
public class RandomTypeConfusionMutator implements BodyMutator {
    private static final List<Object> TYPE_CONFUSION_VALUES = List.of(
            List.of(),
            Collections.singletonList(null),
            Arrays.asList(0, -1, "", true),
            Map.of(),
            Map.of("$ne", "", "$gt", -1),
            Collections.singletonMap("id", null),
            true,
            false);

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object value = TYPE_CONFUSION_VALUES.get(CatsRandom.instance().nextInt(TYPE_CONFUSION_VALUES.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, value).json();
    }

    @Override
    public String description() {
        return "replace field with unexpected JSON array, object, or boolean types";
    }
}
