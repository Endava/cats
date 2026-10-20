package com.endava.cats.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class NounFormsCorpusTest {
    private static final double RECALL_FLOOR = 0.985;
    private static final Pattern PATH_PARAM = Pattern.compile("/([A-Za-z][A-Za-z0-9_-]*)/\\{([A-Za-z][A-Za-z0-9_-]*)}");
    private static final List<String> PARAM_SUFFIXES = List.of("uuid", "guid", "code", "name", "key", "id");
    private static final List<String[]> DIFFERENT_NOUNS = List.of(
            new String[]{"objective", "object"}, new String[]{"organization", "organ"},
            new String[]{"settings", "set"}, new String[]{"activity", "active"},
            new String[]{"generation", "general"}, new String[]{"business", "bus"},
            new String[]{"case", "cache"}, new String[]{"status", "state"},
            new String[]{"policy", "police"}, new String[]{"product", "production"},
            new String[]{"states", "stats"}, new String[]{"cares", "car"},
            new String[]{"pines", "pin"}, new String[]{"mines", "min"},
            new String[]{"sites", "sit"}, new String[]{"notes", "not"});

    @Test
    void shouldMatchResourceSegmentsWithTheirPathParameterNouns() throws IOException {
        Set<String> pairs = new LinkedHashSet<>();
        for (Path file : corpusFiles()) {
            Matcher matcher = PATH_PARAM.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                groundTruthPair(normalize(matcher.group(1)), normalize(matcher.group(2)))
                        .ifPresent(pair -> pairs.add(pair[0] + "|" + pair[1]));
            }
        }

        List<String> misses = new ArrayList<>();
        for (String pair : pairs) {
            int sep = pair.indexOf('|');
            String first = pair.substring(0, sep);
            String second = pair.substring(sep + 1);
            if (!WordUtils.sameNoun(first, second)) {
                misses.add(first + " <-> " + second);
            }
        }
        double recall = pairs.isEmpty() ? 1.0 : (pairs.size() - misses.size()) / (double) pairs.size();
        System.out.printf("Noun corpus: total=%d, recall=%.4f%n", pairs.size(), recall);
        misses.forEach(miss -> System.out.println("MISS: " + miss));

        Assertions.assertThat(pairs).isNotEmpty();
        Assertions.assertThat(recall).isGreaterThanOrEqualTo(RECALL_FLOOR);
        for (String[] different : DIFFERENT_NOUNS) {
            Assertions.assertThat(WordUtils.sameNoun(different[0], different[1])).isFalse();
        }
    }

    private static Optional<String[]> groundTruthPair(String segment, String param) {
        for (String suffix : PARAM_SUFFIXES) {
            if (param.endsWith(suffix)) {
                String noun = param.substring(0, param.length() - suffix.length());
                if (noun.length() < 3 || segment.length() < 3 || !segment.substring(0, 3).equals(noun.substring(0, 3))) {
                    return Optional.empty();
                }
                if (!WordUtils.sameNoun(segment, noun) && (isProperPrefixOfAnyForm(segment, noun) || isProperPrefixOfAnyForm(noun, segment))) {
                    return Optional.empty();
                }
                boolean abbreviation = segment.startsWith(noun) && segment.length() - noun.length() > 2 && !noun.endsWith("s");
                return abbreviation ? Optional.empty() : Optional.of(new String[]{segment, noun});
            }
        }
        return Optional.empty();
    }

    private static boolean isProperPrefixOfAnyForm(String token, String other) {
        return WordUtils.nounForms(token).stream().anyMatch(form -> other.length() > form.length() && other.startsWith(form));
    }

    private static List<Path> corpusFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Paths.get("src/test/resources"))) {
            stream.filter(NounFormsCorpusTest::isSpecFile).forEach(files::add);
        }
        String extraDir = System.getProperty("cats.nounCorpusDir");
        if (extraDir != null && !extraDir.isBlank()) {
            try (Stream<Path> stream = Files.walk(Paths.get(extraDir))) {
                stream.filter(NounFormsCorpusTest::isSpecFile).forEach(files::add);
            }
        }
        return files;
    }

    private static boolean isSpecFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"));
    }

    private static String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
