package com.endava.cats.report.error.template;

import com.endava.cats.report.error.mask.QuotedFragments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Mines error templates with Drain (He et al., <i>Drain: An Online Log Parsing Approach with Fixed Depth Tree</i>,
 * ICWS 2017): a token-count layer, one prefix-token layer, at most 100 children per node, and first tokens containing
 * digits routed to the wildcard branch (as in Drain3).
 * <p>
 * Unlike Drain's single similarity ratio, a sample joins a template only when at most
 * {@code max(1, floor(n * (1 - similarity)))} of its {@code n} tokens differ, with existing wildcards counted as
 * differences. Error messages are much shorter than log lines, so a plain ratio either merges distinct crash sites in
 * long messages or splits short messages that differ by one echoed value. Counting wildcards as differences also
 * means a template never holds more wildcards than that allowance, so it cannot keep generalizing by chaining.
 * Quoted code identifiers ({@code (reading 'id')}, {@code "String.length()"}) are never generalized, because they name
 * the crash site or the validated field.
 * Calibrated on 13.9k RESTgym error responses: similarity {@code 0.8}.
 * <p>
 * Samples are only compared within their {@link ErrorSample#partitionKey() partition}. Mining runs in two passes so the
 * outcome depends as little as possible on the order errors were observed in: the first pass learns templates, the
 * second assigns every sample to the most specific final template that matches it.
 */
public final class ErrorTemplateMiner {
    static final String WILDCARD = "<*>";
    static final double DEFAULT_SIMILARITY = 0.8;
    private static final int MAX_CHILDREN = 100;
    private static final String NO_PREFIX = "";

    private final double similarity;
    private final Map<LengthNodeKey, Map<String, List<TemplateCluster>>> tree = new HashMap<>();
    private int nextClusterId;

    private ErrorTemplateMiner(double similarity) {
        this.similarity = similarity;
    }

    /**
     * Mines the templates of the given samples with the default similarity.
     *
     * @param samples the error samples, in observation order
     * @return one template per sample, in the same order as the samples
     */
    public static List<ErrorTemplate> mine(List<ErrorSample> samples) {
        return mine(samples, DEFAULT_SIMILARITY);
    }

    static List<ErrorTemplate> mine(List<ErrorSample> samples, double similarity) {
        ErrorTemplateMiner miner = new ErrorTemplateMiner(similarity);
        List<TemplateCluster> learned = samples.stream().map(miner::learn).toList();
        return IntStream.range(0, samples.size())
                .mapToObj(index -> templateOf(samples.get(index), learned.get(index)))
                .toList();
    }

    private static ErrorTemplate templateOf(ErrorSample sample, TemplateCluster learned) {
        return ErrorTemplate.of(sample, mostSpecificMatch(sample, learned).tokens());
    }

    private static TemplateCluster mostSpecificMatch(ErrorSample sample, TemplateCluster learned) {
        List<String> tokens = sample.signature().tokens();
        return learned.leaf().stream()
                .filter(cluster -> cluster.matches(tokens))
                .min(Comparator.comparingLong(TemplateCluster::wildcardCount).thenComparingInt(TemplateCluster::id))
                .orElse(learned);
    }

    private TemplateCluster learn(ErrorSample sample) {
        List<String> tokens = sample.signature().tokens();
        return searchLeaf(sample)
                .flatMap(leaf -> bestMatch(leaf, tokens))
                .map(cluster -> cluster.absorb(tokens))
                .orElseGet(() -> createCluster(sample));
    }

    private Optional<List<TemplateCluster>> searchLeaf(ErrorSample sample) {
        Map<String, List<TemplateCluster>> lengthNode = tree.get(LengthNodeKey.of(sample));
        if (lengthNode == null) {
            return Optional.empty();
        }
        String prefix = firstToken(sample);
        return Optional.ofNullable(lengthNode.get(lengthNode.containsKey(prefix) ? prefix : WILDCARD));
    }

    private Optional<TemplateCluster> bestMatch(List<TemplateCluster> leaf, List<String> tokens) {
        long allowedDifferences = allowedDifferences(tokens.size());
        return leaf.stream()
                .filter(cluster -> cluster.differences(tokens) <= allowedDifferences)
                .min(Comparator.<TemplateCluster>comparingLong(cluster -> cluster.differences(tokens))
                        .thenComparing(Comparator.comparingLong(TemplateCluster::wildcardCount).reversed())
                        .thenComparingInt(TemplateCluster::id));
    }

    private long allowedDifferences(int tokenCount) {
        return Math.max(1, (long) Math.floor(tokenCount * (1 - similarity) + 1e-9));
    }

    private TemplateCluster createCluster(ErrorSample sample) {
        Map<String, List<TemplateCluster>> lengthNode = tree.computeIfAbsent(LengthNodeKey.of(sample), _ -> new LinkedHashMap<>());
        List<TemplateCluster> leaf = lengthNode.computeIfAbsent(insertionPrefix(lengthNode, sample), _ -> new ArrayList<>());
        TemplateCluster cluster = new TemplateCluster(nextClusterId++, leaf, sample.signature().tokens());
        leaf.add(cluster);
        return cluster;
    }

    private static String insertionPrefix(Map<String, List<TemplateCluster>> lengthNode, ErrorSample sample) {
        String first = firstToken(sample);
        if (first.isEmpty() || lengthNode.containsKey(first)) {
            return first;
        }
        boolean routeToWildcard = hasDigit(first) || lengthNode.size() >= MAX_CHILDREN - 1;
        return routeToWildcard ? WILDCARD : first;
    }

    private static String firstToken(ErrorSample sample) {
        List<String> tokens = sample.signature().tokens();
        return tokens.isEmpty() ? NO_PREFIX : tokens.getFirst();
    }

    private static boolean hasDigit(String token) {
        return token.chars().anyMatch(Character::isDigit);
    }

    private record LengthNodeKey(String partition, int length) {
        static LengthNodeKey of(ErrorSample sample) {
            return new LengthNodeKey(sample.partitionKey(), sample.signature().tokens().size());
        }
    }

    /**
     * A Drain log group. Its template only ever generalizes (tokens turn into {@link #WILDCARD}), so every sample that
     * joined it keeps matching it.
     */
    private static final class TemplateCluster {
        private final int id;
        private final List<TemplateCluster> leaf;
        private List<String> tokens;

        private TemplateCluster(int id, List<TemplateCluster> leaf, List<String> tokens) {
            this.id = id;
            this.leaf = leaf;
            this.tokens = tokens;
        }

        int id() {
            return id;
        }

        List<TemplateCluster> leaf() {
            return leaf;
        }

        List<String> tokens() {
            return tokens;
        }

        TemplateCluster absorb(List<String> sampleTokens) {
            tokens = IntStream.range(0, tokens.size()).mapToObj(index -> mergedToken(index, sampleTokens)).toList();
            return this;
        }

        private String mergedToken(int index, List<String> sampleTokens) {
            return tokens.get(index).equals(sampleTokens.get(index)) ? tokens.get(index) : WILDCARD;
        }

        /**
         * Counts the positions that differ, or returns {@link Long#MAX_VALUE} when a pinned token (a quoted code
         * identifier, see {@link QuotedFragments#isPinned(String)}) differs: such samples never share a template.
         */
        long differences(List<String> sampleTokens) {
            List<Integer> differing = IntStream.range(0, tokens.size())
                    .filter(index -> isWildcard(index) || !tokens.get(index).equals(sampleTokens.get(index)))
                    .boxed().toList();
            boolean pinnedDiffers = differing.stream()
                    .anyMatch(index -> QuotedFragments.isPinned(tokens.get(index)) || QuotedFragments.isPinned(sampleTokens.get(index)));
            return pinnedDiffers ? Long.MAX_VALUE : differing.size();
        }

        boolean matches(List<String> sampleTokens) {
            return IntStream.range(0, tokens.size())
                    .allMatch(index -> isWildcard(index) || tokens.get(index).equals(sampleTokens.get(index)));
        }

        long wildcardCount() {
            return tokens.stream().filter(WILDCARD::equals).count();
        }

        private boolean isWildcard(int index) {
            return WILDCARD.equals(tokens.get(index));
        }
    }
}
