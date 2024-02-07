package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Sends a random json payloads replacing the entire input json.
 */
@Singleton
public class RandomJsonMutator implements Mutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        List<String> randomPayloads = UnicodeGenerator.getInvalidJsons();

        return randomPayloads.get(CatsUtil.random().nextInt(randomPayloads.size()));
    }

    @Override
    public String name() {
        return "random json";
    }
}