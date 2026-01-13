package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.CatsRandom;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Sends a random json payloads replacing the entire input json.
 */
@Singleton
public class RandomJsonMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        List<String> randomPayloads = UnicodeGenerator.getInvalidJsons();

        return randomPayloads.get(CatsRandom.instance().nextInt(randomPayloads.size()));
    }

    @Override
    public String description() {
        return "replace body with random invalid jsons";
    }
}