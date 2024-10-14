package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

/**
 * Removes the field from the json body.
 */
@Singleton
public class RemoveFieldMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        return JsonUtils.deleteNode(inputJson, selectedField);
    }

    @Override
    public String description() {
        return "remove field from the body";
    }
}
