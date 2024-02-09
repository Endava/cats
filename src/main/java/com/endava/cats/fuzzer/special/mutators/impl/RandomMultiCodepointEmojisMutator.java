package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends random multi codepoint emojis in the target field.
 */
@Singleton
public class RandomMultiCodepointEmojisMutator implements Mutator {
    private static final int BOUND = 15;
    private final CatsUtil catsUtil;

    @Inject
    public RandomMultiCodepointEmojisMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        String randomEmojis = generateEmojiString();

        return catsUtil.justReplaceField(inputJson, selectedField, randomEmojis).json();
    }

    private static String generateEmojiString() {
        StringBuilder sb = new StringBuilder();
        int minHighSurrogate = 0xD83D; // Start of high surrogate range
        int maxHighSurrogate = 0xD83E; // End of high surrogate range
        int minLowSurrogate = 0xDC00; // Start of low surrogate range
        int maxLowSurrogate = 0xDFFF; // End of low surrogate range

        for (int i = 0; i < BOUND; i++) {
            int highSurrogate = CatsUtil.random().nextInt(maxHighSurrogate - minHighSurrogate + 1) + minHighSurrogate;
            int lowSurrogate = CatsUtil.random().nextInt(maxLowSurrogate - minLowSurrogate + 1) + minLowSurrogate;

            sb.append(Character.toChars(highSurrogate));
            sb.append(Character.toChars(lowSurrogate));
        }
        return sb.toString();
    }

    @Override
    public String name() {
        return "random multi codepoint emojis";
    }
}