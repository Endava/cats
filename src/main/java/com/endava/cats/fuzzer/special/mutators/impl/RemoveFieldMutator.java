package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.json.JsonUtils;
import jakarta.inject.Singleton;

/**
 * Removes the field from the json body.
 */
@Singleton
public class RemoveFieldMutator implements Mutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        return JsonUtils.deleteNode(inputJson, selectedField);
    }

    @Override
    public String description() {
        return "remove field from the body";
    }
}
