package com.endava.cats.fuzzer.special.mutators;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides utility methods for mutating strings based on the differences between two strings.
 */
public class StringMutationUtils {

    public static String applySameMutation(String initial, String mutated, String target) {
        List<Insertion> insertions = computeInsertions(initial, mutated);

        return applyInsertions(target, insertions, initial.length());
    }

    public static String extractMutatedString(String initial, String text) {
        String regex = buildRegexFromInitialString(initial);

        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.UNICODE_CASE);

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        } else {
            return null;
        }
    }

    private static String buildRegexFromInitialString(String initial) {
        StringBuilder regex = new StringBuilder();

        for (int i = 0; i < initial.length(); i++) {
            String s = String.valueOf(initial.charAt(i));

            String escapedChar = Pattern.quote(s);
            regex.append(escapedChar);
            if (i < initial.length() - 1) {
                regex.append(".*?");
            }
        }
        return regex.toString();
    }


    private static List<Insertion> computeInsertions(String initial, String mutated) {
        List<Insertion> insertions = new ArrayList<>();
        int i = 0, j = 0;
        while (i < initial.length() && j < mutated.length()) {
            if (initial.charAt(i) == mutated.charAt(j)) {
                i++;
                j++;
            } else {
                StringBuilder insertedChars = new StringBuilder();
                while (j < mutated.length() && initial.charAt(i) != mutated.charAt(j)) {
                    insertedChars.append(mutated.charAt(j));
                    j++;
                }
                insertions.add(new Insertion(i, insertedChars.toString(), initial.length()));
            }
        }

        if (j < mutated.length()) {
            StringBuilder insertedChars = new StringBuilder();
            while (j < mutated.length()) {
                insertedChars.append(mutated.charAt(j));
                j++;
            }
            insertions.add(new Insertion(i, insertedChars.toString(), initial.length()));
        }
        return insertions;
    }

    private static String applyInsertions(String target, List<Insertion> insertions, int initialLength) {
        StringBuilder result = new StringBuilder(target);
        for (Insertion ins : insertions) {
            int targetPos = (int) Math.round(((double) ins.position / initialLength) * target.length());
            result.insert(targetPos + ins.offset, ins.characters);
            ins.offset += ins.characters.length();
        }
        return result.toString();
    }

    private static class Insertion {
        int position;
        String characters;
        int offset = 0;

        Insertion(int position, String characters, int initialLength) {
            this.position = position;
            this.characters = characters;
        }
    }
}

