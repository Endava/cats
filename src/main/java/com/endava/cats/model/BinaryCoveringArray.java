package com.endava.cats.model;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates deterministic binary covering arrays for field-removal combinations.
 * <p>
 * Strength 2 uses the optimal closed-form construction of Kleitman &amp; Spencer (1973) and Katona (1973),
 * assigning each factor a distinct equal-weight binary column vector of length {@code N - 1} and weight
 * {@code ceil(N / 2)}. Strength 3 uses deterministic greedy t-way covering with 1-flip local refinement.
 */
public final class BinaryCoveringArray {
    /**
     * Maximum number of optional fields included in a strength-2 covering array before capping.
     */
    public static final int MAX_FACTORS = 200;

    /**
     * Maximum number of optional fields included in a strength-3 covering array before capping.
     */
    public static final int MAX_STRENGTH_3_FACTORS = 50;

    /**
     * Default pairwise interaction strength.
     */
    public static final int DEFAULT_STRENGTH = 2;

    private static final int MIN_STRENGTH = 2;
    private static final int MAX_STRENGTH = 3;
    private static final int UNSET = -1;
    private static final int ALL_NON_ZERO_TRIPLES_MASK = 0b1111_1110;
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(BinaryCoveringArray.class);

    private BinaryCoveringArray() {
    }

    /**
     * Generates a strength-2 binary covering array of field-removal subsets over the given optional fields.
     *
     * @param optionalFields the optional field names
     * @return deterministic set of field-removal subsets covering all 2-field presence/absence interactions
     */
    public static Set<Set<String>> generate(Collection<String> optionalFields) {
        return generate(optionalFields, DEFAULT_STRENGTH);
    }

    /**
     * Generates a binary covering array of the requested interaction strength (2 or 3) over the given optional fields.
     *
     * @param optionalFields the optional field names
     * @param strength       interaction strength (2 or 3)
     * @return deterministic set of field-removal subsets covering all {@code strength}-way interactions
     */
    public static Set<Set<String>> generate(Collection<String> optionalFields, int strength) {
        validateStrength(strength);
        if (optionalFields == null || optionalFields.size() <= 1) {
            return Set.of();
        }
        List<String> sortedFields = capAndSortFields(optionalFields, strength);
        List<boolean[]> matrix = generateMatrix(sortedFields.size(), strength);
        return toFieldSets(sortedFields, matrix);
    }

    /**
     * Generates the non-zero rows of a binary covering array for {@code factors} columns and {@code strength} (2 or 3).
     * Row 0 (all {@code false}, representing the unmodified baseline request) is implicit and omitted.
     *
     * @param factors  number of binary factors
     * @param strength interaction strength (2 or 3)
     * @return list of boolean rows of length {@code factors}, where {@code true} means remove the field
     */
    public static List<boolean[]> generateMatrix(int factors, int strength) {
        validateStrength(strength);
        if (factors <= 1) {
            return List.of();
        }
        return strength == DEFAULT_STRENGTH
                ? generateStrength2Matrix(factors)
                : generateStrength3Matrix(factors);
    }

    /**
     * Prunes child fields from a removal set whenever any of their ancestor object fields is also removed in the same set.
     *
     * @param removedFields raw set of fields marked for removal
     * @return pruned set with redundant descendant fields removed
     */
    public static Set<String> pruneNestedFields(Set<String> removedFields) {
        return removedFields.stream()
                .filter(field -> !hasRemovedAncestor(field, removedFields))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Computes the smallest total covering-array row count {@code N} (including the all-zero baseline row)
     * such that {@code C(N - 1, ceil(N / 2)) >= factors}.
     *
     * @param factors number of binary factors ({@code >= 2})
     * @return optimal total row count {@code N}
     */
    public static int optimalStrength2TotalRows(int factors) {
        if (factors <= 1) {
            return 1;
        }
        int totalRows = 4;
        while (binomial(totalRows - 1, (totalRows + 1) / 2) < factors) {
            totalRows++;
        }
        return totalRows;
    }

    static long binomial(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        int symmetricK = Math.min(k, n - k);
        long result = 1;
        for (int i = 1; i <= symmetricK; i++) {
            result = (result * (n - symmetricK + i)) / i;
        }
        return result;
    }

    private static void validateStrength(int strength) {
        if (strength < MIN_STRENGTH || strength > MAX_STRENGTH) {
            throw new IllegalArgumentException("Interaction strength must be 2 or 3, got: " + strength);
        }
    }

    private static List<String> capAndSortFields(Collection<String> optionalFields, int strength) {
        int cap = strength == DEFAULT_STRENGTH ? MAX_FACTORS : MAX_STRENGTH_3_FACTORS;
        List<String> sorted = optionalFields.stream().distinct().sorted().toList();
        if (sorted.size() > cap) {
            LOGGER.note("Optional fields count [{}] exceeds pairwise cap; capping at [{}] fields", sorted.size(), cap);
            return sorted.subList(0, cap);
        }
        return sorted;
    }

    private static Set<Set<String>> toFieldSets(List<String> sortedFields, List<boolean[]> matrix) {
        Set<Set<String>> sets = new LinkedHashSet<>();
        for (boolean[] row : matrix) {
            Set<String> rawSet = extractRowFields(sortedFields, row);
            Set<String> pruned = pruneNestedFields(rawSet);
            if (!pruned.isEmpty()) {
                sets.add(pruned);
            }
        }
        return sets;
    }

    private static Set<String> extractRowFields(List<String> sortedFields, boolean[] row) {
        Set<String> removed = new LinkedHashSet<>();
        for (int i = 0; i < sortedFields.size(); i++) {
            if (row[i]) {
                removed.add(sortedFields.get(i));
            }
        }
        return removed;
    }

    private static boolean hasRemovedAncestor(String field, Set<String> removedFields) {
        int separator = field.indexOf('#');
        while (separator > 0) {
            if (removedFields.contains(field.substring(0, separator))) {
                return true;
            }
            separator = field.indexOf('#', separator + 1);
        }
        return false;
    }

    private static List<boolean[]> generateStrength2Matrix(int factors) {
        int totalRows = optimalStrength2TotalRows(factors);
        int nonZeroRows = totalRows - 1;
        int weight = (totalRows + 1) / 2;
        List<boolean[]> columns = new ArrayList<>(factors);
        VectorSearch search = new VectorSearch(nonZeroRows, factors, columns);
        collectWeightVectors(search, new boolean[nonZeroRows], 0, weight);
        return transpose(columns, nonZeroRows, factors);
    }

    private static void collectWeightVectors(VectorSearch search, boolean[] current, int index, int remainingOnes) {
        if (search.result().size() >= search.limit()) {
            return;
        }
        if (index == search.length()) {
            search.result().add(current.clone());
            return;
        }
        int remainingSlots = search.length() - 1 - index;
        if (remainingSlots >= remainingOnes) {
            current[index] = false;
            collectWeightVectors(search, current, index + 1, remainingOnes);
        }
        if (remainingOnes > 0 && search.result().size() < search.limit()) {
            current[index] = true;
            collectWeightVectors(search, current, index + 1, remainingOnes - 1);
        }
    }

    private static List<boolean[]> transpose(List<boolean[]> columns, int rowCount, int colCount) {
        List<boolean[]> rows = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            boolean[] row = new boolean[colCount];
            for (int c = 0; c < colCount; c++) {
                row[c] = columns.get(c)[r];
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<boolean[]> generateStrength3Matrix(int factors) {
        if (factors <= MAX_STRENGTH) {
            return exhaustiveNonZeroMatrix(factors);
        }
        TripleCoverageTable table = new TripleCoverageTable(factors);
        List<boolean[]> rows = new ArrayList<>();
        while (table.remainingUncovered() > 0) {
            int[] row = buildNextStrength3Row(factors, table);
            refineRowBySingleFlips(row, factors, table);
            table.markCovered(row);
            rows.add(toBooleanRow(row));
        }
        return rows;
    }

    private static List<boolean[]> exhaustiveNonZeroMatrix(int factors) {
        int total = 1 << factors;
        List<boolean[]> rows = new ArrayList<>(total - 1);
        for (int mask = 1; mask < total; mask++) {
            boolean[] row = new boolean[factors];
            for (int c = 0; c < factors; c++) {
                row[c] = ((mask >> (factors - 1 - c)) & 1) == 1;
            }
            rows.add(row);
        }
        return rows;
    }

    private static int[] buildNextStrength3Row(int factors, TripleCoverageTable table) {
        int[] row = new int[factors];
        Arrays.fill(row, UNSET);
        table.seedMostUncoveredTriple(row);
        while (hasUnsetColumn(row)) {
            if (!selectNextPositiveGainColumn(row, factors, table)
                    && !table.applyNextCompatibleUncoveredTriple(row)) {
                fillRemainingUnsetColumns(row, factors);
            }
        }
        return row;
    }

    private static boolean hasUnsetColumn(int[] row) {
        for (int val : row) {
            if (val == UNSET) {
                return true;
            }
        }
        return false;
    }

    private static boolean selectNextPositiveGainColumn(int[] row, int factors, TripleCoverageTable table) {
        int bestCol = UNSET;
        int bestBit = 0;
        int bestScore = 0;
        boolean preferOne = shouldPreferOne(row, factors);
        for (int col = 0; col < factors; col++) {
            if (row[col] == UNSET) {
                int gain0 = table.countColumnGain(row, col, 0);
                int gain1 = table.countColumnGain(row, col, 1);
                int maxGain = Math.max(gain0, gain1);
                int score = maxGain * 16 + Math.min(gain0, gain1);
                if (maxGain > 0 && score > bestScore) {
                    bestScore = score;
                    bestCol = col;
                    bestBit = chooseBit(gain0, gain1, preferOne);
                }
            }
        }
        if (bestCol == UNSET) {
            return false;
        }
        row[bestCol] = bestBit;
        return true;
    }

    private static void fillRemainingUnsetColumns(int[] row, int factors) {
        for (int col = 0; col < factors; col++) {
            if (row[col] == UNSET) {
                row[col] = shouldPreferOne(row, factors) ? 1 : 0;
            }
        }
    }

    private static boolean shouldPreferOne(int[] row, int factors) {
        int ones = 0;
        int setCols = 0;
        for (int i = 0; i < factors; i++) {
            if (row[i] != UNSET) {
                setCols++;
                ones += row[i];
            }
        }
        return ones * 2 <= setCols;
    }

    private static int chooseBit(int gain0, int gain1, boolean preferOne) {
        if (gain1 != gain0) {
            return gain1 > gain0 ? 1 : 0;
        }
        return preferOne ? 1 : 0;
    }

    private static void refineRowBySingleFlips(int[] row, int factors, TripleCoverageTable table) {
        boolean improved = true;
        while (improved) {
            improved = tryFlipSingleColumn(row, factors, table);
        }
    }

    private static boolean tryFlipSingleColumn(int[] row, int factors, TripleCoverageTable table) {
        for (int col = 0; col < factors; col++) {
            int currentBit = row[col];
            int currentGain = table.countColumnGain(row, col, currentBit);
            int flippedGain = table.countColumnGain(row, col, 1 - currentBit);
            if (flippedGain > currentGain) {
                row[col] = 1 - currentBit;
                return true;
            }
        }
        return false;
    }

    private static boolean[] toBooleanRow(int[] row) {
        boolean[] result = new boolean[row.length];
        for (int i = 0; i < row.length; i++) {
            result[i] = row[i] == 1;
        }
        return result;
    }

    private record VectorSearch(int length, int limit, List<boolean[]> result) {
    }

    private record ColumnCandidate(int col, int bit) {
    }

    private static final class TripleSlot {
        private final int i;
        private final int j;
        private final int k;
        private int mask = ALL_NON_ZERO_TRIPLES_MASK;

        TripleSlot(int i, int j, int k) {
            this.i = i;
            this.j = j;
            this.k = k;
        }

        int uncoveredCount() {
            return Integer.bitCount(mask);
        }

        int weightForCombo(int combo) {
            if (((mask >> combo) & 1) == 0) {
                return 0;
            }
            return 4 + Integer.bitCount(mask);
        }

        void applyHighestUncoveredTo(int[] row) {
            int combo = 31 - Integer.numberOfLeadingZeros(mask);
            applyCombo(row, combo);
        }

        boolean tryApplyCompatibleTo(int[] row) {
            if (mask == 0) {
                return false;
            }
            for (int combo = 7; combo >= 1; combo--) {
                if (((mask >> combo) & 1) != 0 && isCompatibleWith(row, combo)) {
                    applyCombo(row, combo);
                    return true;
                }
            }
            return false;
        }

        boolean isCompatibleWith(int[] row, int combo) {
            int vi = (combo >> 2) & 1;
            int vj = (combo >> 1) & 1;
            int vk = combo & 1;
            return (row[i] == UNSET || row[i] == vi)
                    && (row[j] == UNSET || row[j] == vj)
                    && (row[k] == UNSET || row[k] == vk);
        }

        void applyCombo(int[] row, int combo) {
            row[i] = (combo >> 2) & 1;
            row[j] = (combo >> 1) & 1;
            row[k] = combo & 1;
        }

        boolean markCoveredBy(int[] row) {
            int combo = (row[i] << 2) | (row[j] << 1) | row[k];
            int bit = 1 << combo;
            if ((mask & bit) == 0) {
                return false;
            }
            mask &= ~bit;
            return true;
        }
    }

    private static final class TripleCoverageTable {
        private final int factors;
        private final TripleSlot[][][] index;
        private final List<TripleSlot> slots;
        private int remaining;

        TripleCoverageTable(int factors) {
            this.factors = factors;
            this.index = new TripleSlot[factors][factors][factors];
            this.slots = new ArrayList<>();
            initSlots();
        }

        private void initSlots() {
            for (int i = 0; i < factors; i++) {
                initSlotsForFirst(i);
            }
        }

        private void initSlotsForFirst(int i) {
            for (int j = i + 1; j < factors; j++) {
                for (int k = j + 1; k < factors; k++) {
                    TripleSlot slot = new TripleSlot(i, j, k);
                    index[i][j][k] = slot;
                    slots.add(slot);
                    remaining += 7;
                }
            }
        }

        int remainingUncovered() {
            return remaining;
        }

        void seedMostUncoveredTriple(int[] row) {
            TripleSlot best = slots.getFirst();
            for (TripleSlot slot : slots) {
                if (slot.uncoveredCount() > best.uncoveredCount()) {
                    best = slot;
                }
            }
            best.applyHighestUncoveredTo(row);
        }

        boolean applyNextCompatibleUncoveredTriple(int[] row) {
            for (TripleSlot slot : slots) {
                if (slot.tryApplyCompatibleTo(row)) {
                    return true;
                }
            }
            return false;
        }

        int countColumnGain(int[] row, int col, int candidateBit) {
            ColumnCandidate candidate = new ColumnCandidate(col, candidateBit);
            int gain = 0;
            for (int a = 0; a < factors; a++) {
                if (a != col && row[a] != UNSET) {
                    gain += countColumnGainWithFirst(row, a, candidate);
                }
            }
            return gain;
        }

        private int countColumnGainWithFirst(int[] row, int a, ColumnCandidate candidate) {
            int gain = 0;
            for (int b = a + 1; b < factors; b++) {
                if (b != candidate.col() && row[b] != UNSET) {
                    gain += lookupWeight(row, a, b, candidate);
                }
            }
            return gain;
        }

        private int lookupWeight(int[] row, int a, int b, ColumnCandidate candidate) {
            int va = row[a];
            int vb = row[b];
            int col = candidate.col();
            int vc = candidate.bit();
            if (col < a) {
                return index[col][a][b].weightForCombo((vc << 2) | (va << 1) | vb);
            }
            if (col < b) {
                return index[a][col][b].weightForCombo((va << 2) | (vc << 1) | vb);
            }
            return index[a][b][col].weightForCombo((va << 2) | (vb << 1) | vc);
        }

        void markCovered(int[] row) {
            for (TripleSlot slot : slots) {
                if (slot.markCoveredBy(row)) {
                    remaining--;
                }
            }
        }
    }
}
