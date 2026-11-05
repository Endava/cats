package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies sibling domain fields that represent mutually exclusive business inputs
 * (such as {@code discountAmount}/{@code discountPercentage}, {@code creditAmount}/{@code debitAmount},
 * {@code includeIds}/{@code excludeIds}, or {@code endDate}/{@code durationDays}) and populates both
 * simultaneously to verify mutual-exclusion validation.
 */
@Singleton
@FieldFuzzer
public class MutuallyExclusiveFieldsFuzzer implements Fuzzer {
    record ExclusiveGroup(Set<String> firstCandidates, Set<String> secondCandidates) {
    }

    record ExclusivePair(String firstField, String secondField) {
    }

    private static final List<ExclusiveGroup> EXCLUSIVE_GROUPS = List.of(
            new ExclusiveGroup(
                    Set.of("discountamount", "flatdiscount"),
                    Set.of("discountpercent", "discountpercentage", "discountrate")),
            new ExclusiveGroup(
                    Set.of("creditamount", "credit"),
                    Set.of("debitamount", "debit")),
            new ExclusiveGroup(
                    Set.of("includeids", "includedids", "allowlist", "whitelist"),
                    Set.of("excludeids", "excludedids", "blocklist", "blacklist")),
            new ExclusiveGroup(
                    Set.of("enddate", "enddatetime", "expiresat", "expirydate", "expirationdate"),
                    Set.of("duration", "durationdays", "durationseconds", "durationminutes", "ttl", "ttlseconds"))
    );

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(MutuallyExclusiveFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new MutuallyExclusiveFieldsFuzzer instance.
     *
     * @param simpleExecutor the executor used to run the fuzz logic
     */
    public MutuallyExclusiveFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        List<ExclusivePair> pairs = findExclusivePairs(data);
        if (pairs.isEmpty()) {
            logger.skip("No mutually exclusive field pairs found in the request");
            return;
        }
        pairs.forEach(pair -> fuzzExclusivePair(data, pair));
    }

    private void fuzzExclusivePair(FuzzingData data, ExclusivePair pair) {
        if (!JsonUtils.isFieldInJson(data.getPayload(), pair.firstField())
                || !JsonUtils.isFieldInJson(data.getPayload(), pair.secondField())) {
            return;
        }
        Object firstVal = activeValue(data, pair.firstField());
        Object secondVal = activeValue(data, pair.secondField());
        String withFirst = CatsUtil.justReplaceField(data.getPayload(), pair.firstField(), firstVal).json();
        String mutatedPayload = CatsUtil.justReplaceField(withFirst, pair.secondField(), secondVal).json();

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .mutationTargets(RequestTargetResolver.resolvePayloadFields(
                                data, List.of(pair.firstField(), pair.secondField())))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Send mutually exclusive fields [%s] and [%s] simultaneously with active values to verify mutual-exclusion validation"
                                .formatted(pair.firstField(), pair.secondField()))
                        .build()
        );
    }

    static Object activeValue(FuzzingData data, String field) {
        Object current = JsonUtils.getVariableFromJson(data.getPayload(), field);
        if (current instanceof Number num) {
            return num.doubleValue() > 0 ? current : 10;
        }
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(field);
        if (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema)) {
            return 10;
        }
        if (!JsonUtils.isNotSet(String.valueOf(current)) && !String.valueOf(current).isBlank()) {
            return current;
        }
        return "catsActiveValue";
    }

    static List<ExclusivePair> findExclusivePairs(FuzzingData data) {
        Set<String> allFields = Optional.ofNullable(data.getAllFieldsByHttpMethod()).orElse(Set.of());
        return EXCLUSIVE_GROUPS.stream()
                .flatMap(group -> matchGroupPairs(allFields, group).stream())
                .sorted(Comparator.comparing(ExclusivePair::firstField).thenComparing(ExclusivePair::secondField))
                .toList();
    }

    private static List<ExclusivePair> matchGroupPairs(Set<String> allFields, ExclusiveGroup group) {
        return allFields.stream()
                .filter(field -> group.firstCandidates().contains(WordUtils.normalizeLeaf(field)))
                .flatMap(first -> allFields.stream()
                        .filter(second -> sameParentPrefix(first, second))
                        .filter(second -> group.secondCandidates().contains(WordUtils.normalizeLeaf(second)))
                        .map(second -> new ExclusivePair(first, second)))
                .toList();
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
        return "populates mutually exclusive sibling fields (e.g., discountAmount and discountPercentage, creditAmount and debitAmount) simultaneously to verify mutual-exclusion validation";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
