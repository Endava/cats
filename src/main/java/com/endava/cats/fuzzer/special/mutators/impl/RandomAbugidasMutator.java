package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

/**
 * Sends a random abugidas chars in the target field.
 */
@Singleton
public class RandomAbugidasMutator implements BodyMutator {
    // Telugu script characters for consonants, vowels, and modifiers
    private static final String[] TELUGU_CONSONANTS = {"క", "ఖ", "గ", "ఘ", "ఙ", "చ", "ఛ", "జ", "ఝ", "ఞ", "ట", "ఠ", "డ", "ఢ", "ణ", "త", "థ", "ద", "ధ", "న", "ప", "ఫ", "బ", "భ", "మ", "య", "ర", "ల", "వ", "శ", "ష", "స", "హ", "ళ", "ఱ"};
    private static final String[] TELUGU_VOWELS = {"అ", "ఆ", "ఇ", "ఈ", "ఉ", "ఊ", "ఋ", "ౠ", "ఌ", "ౡ", "ఎ", "ఏ", "ఐ", "ఒ", "ఓ", "ఔ"};
    private static final String[] TELUGU_MODIFIERS = {"ా", "ి", "ీ", "ు", "ూ", "ృ", "ౄ", "ె", "ే", "ై", "ొ", "ో", "ౌ"};

    // Bengali script characters for consonants, vowels, and modifiers
    private static final String[] BENGALI_CONSONANTS = {"ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ", "ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন", "প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ", "স", "হ", "ড়", "ঢ়", "ড়", "ঢ়", "য়"};
    private static final String[] BENGALI_VOWELS = {"অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "ঌ", "এ", "ঐ", "ও", "ঔ"};
    private static final String[] BENGALI_MODIFIERS = {"া", "ি", "ী", "ু", "ূ", "ৃ", "ৄ", "ে", "ৈ", "ো", "ৌ"};


    @Override
    public String mutate(String inputJson, String selectedField) {
        String existingValue = String.valueOf(JsonUtils.getVariableFromJson(inputJson, selectedField));
        String valueWithAbugidas = generateRandomBengaliCharacter() + existingValue + generateRandomTeluguCharacter();

        return CatsUtil.justReplaceField(inputJson, selectedField, valueWithAbugidas).json();
    }

    @Override
    public String description() {
        return "inject field with random abugidas characters";
    }

    public static String generateRandomTeluguCharacter() {
        String consonant = TELUGU_CONSONANTS[CatsRandom.instance().nextInt(TELUGU_CONSONANTS.length)];
        String vowel = TELUGU_VOWELS[CatsRandom.instance().nextInt(TELUGU_VOWELS.length)];
        String modifier = TELUGU_MODIFIERS[CatsRandom.instance().nextInt(TELUGU_MODIFIERS.length)];

        return consonant + "\u200C" + vowel + modifier;
    }

    public static String generateRandomBengaliCharacter() {
        String consonant = BENGALI_CONSONANTS[CatsRandom.instance().nextInt(BENGALI_CONSONANTS.length)];
        String vowel = BENGALI_VOWELS[CatsRandom.instance().nextInt(BENGALI_VOWELS.length)];
        String modifier = BENGALI_MODIFIERS[CatsRandom.instance().nextInt(BENGALI_MODIFIERS.length)];

        return consonant + "\u200C" + vowel + modifier;
    }
}
