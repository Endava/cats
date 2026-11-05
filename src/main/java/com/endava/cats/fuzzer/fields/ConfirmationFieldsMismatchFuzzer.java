package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies sibling confirmation field pairs (such as {@code password}/{@code confirmPassword},
 * {@code email}/{@code confirmEmail}, or {@code iban}/{@code repeatIban}) and sends a mismatched
 * value in the confirmation field to verify that the backend enforces cross-field equality.
 */
@Singleton
@FieldFuzzer
public class ConfirmationFieldsMismatchFuzzer implements Fuzzer {
    private static final List<String> CONFIRMATION_PREFIXES = List.of(
            "confirm", "repeat", "verify", "retype"
    );
    private static final List<String> CONFIRMATION_SUFFIXES = List.of(
            "confirmation", "verification", "repeat", "verify", "retype", "match"
    );

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ConfirmationFieldsMismatchFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    record ConfirmationPair(String primaryField, String confirmField) {
    }

    /**
     * Creates a new ConfirmationFieldsMismatchFuzzer instance.
     *
     * @param simpleExecutor the executor used to run the fuzz logic
     */
    public ConfirmationFieldsMismatchFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        List<ConfirmationPair> pairs = findConfirmationPairs(data);
        if (pairs.isEmpty()) {
            logger.skip("No confirmation field pairs found in the request");
            return;
        }
        pairs.forEach(pair -> fuzzPair(data, pair));
    }

    private void fuzzPair(FuzzingData data, ConfirmationPair pair) {
        Object primaryVal = JsonUtils.getVariableFromJson(data.getPayload(), pair.primaryField());
        Object confirmVal = JsonUtils.getVariableFromJson(data.getPayload(), pair.confirmField());
        if (JsonUtils.isNotSet(String.valueOf(primaryVal)) || JsonUtils.isNotSet(String.valueOf(confirmVal))) {
            return;
        }
        Object mismatchedConfirm = buildMismatchedValue(primaryVal);
        String withPrimary = CatsUtil.justReplaceField(data.getPayload(), pair.primaryField(), primaryVal).json();
        String mutatedPayload = CatsUtil.justReplaceField(withPrimary, pair.confirmField(), mismatchedConfirm).json();

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .mutationTargets(RequestTargetResolver.resolvePayloadFields(
                                data, List.of(pair.primaryField(), pair.confirmField())))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Send mismatched values for primary field [%s] and confirmation field [%s] to verify cross-field equality validation"
                                .formatted(pair.primaryField(), pair.confirmField()))
                        .build()
        );
    }

    static Object buildMismatchedValue(Object primaryVal) {
        if (primaryVal instanceof Number num) {
            return num.longValue() + 1L;
        }
        String text = String.valueOf(primaryVal);
        if (text.isEmpty()) {
            return "catsMismatch1";
        }
        char first = text.charAt(0);
        char replacement = first == 'X' ? 'Y' : (Character.isDigit(first) ? (first == '9' ? '8' : '9') : 'X');
        return replacement + text.substring(1);
    }

    static List<ConfirmationPair> findConfirmationPairs(FuzzingData data) {
        Set<String> allFields = Optional.ofNullable(data.getAllFieldsByHttpMethod()).orElse(Set.of());
        return allFields.stream()
                .sorted()
                .map(field -> matchSiblingPrimary(field, allFields))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(ConfirmationPair::primaryField).thenComparing(ConfirmationPair::confirmField))
                .toList();
    }

    private static Optional<ConfirmationPair> matchSiblingPrimary(String candidateConfirmField, Set<String> allFields) {
        String normalizedLeaf = WordUtils.normalizeLeaf(candidateConfirmField);
        Optional<String> expectedBase = extractBaseFieldName(normalizedLeaf);
        if (expectedBase.isEmpty()) {
            return Optional.empty();
        }
        String targetBase = expectedBase.get();
        return allFields.stream()
                .filter(other -> !other.equals(candidateConfirmField))
                .filter(other -> sameParentPrefix(candidateConfirmField, other))
                .filter(other -> WordUtils.normalizeLeaf(other).equals(targetBase))
                .findFirst()
                .map(primary -> new ConfirmationPair(primary, candidateConfirmField));
    }

    static Optional<String> extractBaseFieldName(String normalizedLeaf) {
        for (String prefix : CONFIRMATION_PREFIXES) {
            if (normalizedLeaf.startsWith(prefix) && normalizedLeaf.length() > prefix.length()) {
                return Optional.of(normalizedLeaf.substring(prefix.length()));
            }
        }
        for (String suffix : CONFIRMATION_SUFFIXES) {
            if (normalizedLeaf.endsWith(suffix) && normalizedLeaf.length() > suffix.length()) {
                return Optional.of(normalizedLeaf.substring(0, normalizedLeaf.length() - suffix.length()));
            }
        }
        return Optional.empty();
    }

    private static boolean sameParentPrefix(String firstField, String secondField) {
        int idx1 = firstField.lastIndexOf('#');
        int idx2 = secondField.lastIndexOf('#');
        String p1 = idx1 >= 0 ? firstField.substring(0, idx1) : "";
        String p2 = idx2 >= 0 ? secondField.substring(0, idx2) : "";
        return p1.equals(p2);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sends mismatched values in sibling confirmation fields (e.g., password and confirmPassword, email and confirmEmail) to verify cross-field equality validation";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
