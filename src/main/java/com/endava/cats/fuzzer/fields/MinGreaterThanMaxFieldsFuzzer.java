package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A fuzzer that tests API robustness by swapping min/max values in numeric range fields.
 * It identifies field pairs that represent ranges (like minAmount/maxAmount) and
 * deliberately violates their logical relationship to verify the error handling.
 */
@Singleton
@FieldFuzzer
public class MinGreaterThanMaxFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());
    private final SimpleExecutor simpleExecutor;

    private record RangeSuffixPair(String lowSuffix, String highSuffix) {
    }

    private static final List<RangeSuffixPair> RANGE_PATTERNS = List.of(
            new RangeSuffixPair("min", "max"),
            new RangeSuffixPair("from", "to"),
            new RangeSuffixPair("start", "end"),
            new RangeSuffixPair("low", "high"),
            new RangeSuffixPair("lower", "upper"),
            new RangeSuffixPair("begin", "stop"),
            new RangeSuffixPair("begin", "finish"),
            new RangeSuffixPair("first", "last"),
            new RangeSuffixPair("gte", "lte"),
            new RangeSuffixPair("gt", "lt"),
            new RangeSuffixPair("bottom", "top"),
            new RangeSuffixPair("floor", "ceiling")

    );

    public MinGreaterThanMaxFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        for (RangeSuffixPair pattern : RANGE_PATTERNS) {
            Map<String, String> rangePairs = findRangePairs(data, pattern);
            for (Map.Entry<String, String> pair : rangePairs.entrySet()) {
                processRangePair(pair.getKey(), pair.getValue(), data);
            }
        }
    }

    private void processRangePair(String lowField, String highField, FuzzingData data) {
        Object lowValue = JsonUtils.getVariableFromJson(data.getPayload(), lowField);
        Object highValue = JsonUtils.getVariableFromJson(data.getPayload(), highField);

        if (!(lowValue instanceof Number lowNum) || !(highValue instanceof Number highNum)) {
            return;
        }
        Object invalidLowValue = lowValue;
        Object invalidHighValue = highValue;

        if (compareNumbers(lowNum, highNum) < 0) {
            invalidLowValue = highValue;
            invalidHighValue = lowValue;
        }

        String mutatePayloadWithLowField = CatsUtil.justReplaceField(data.getPayload(), lowField, invalidLowValue).json();
        String mutatedPayloadWithHighField = CatsUtil.justReplaceField(mutatePayloadWithLowField, highField, invalidHighValue).json();
        executeTest(mutatedPayloadWithHighField, data, lowField, highField);

    }

    private int compareNumbers(Number n1, Number n2) {
        if (isIntegerType(n1) && isIntegerType(n2)) {
            return Long.compare(n1.longValue(), n2.longValue());
        }
        return Double.compare(n1.doubleValue(), n2.doubleValue());
    }

    private boolean isIntegerType(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    private void executeTest(String mutatedPayload, FuzzingData data, String lowField, String highField) {
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Send a request where field [%s] > field [%s], violating logical relationship"
                                .formatted(lowField, highField))
                        .build()
        );
    }

    private Map<String, String> findRangePairs(FuzzingData data, RangeSuffixPair pattern) {
        Set<String> fields = data.getAllFieldsByHttpMethod();
        Map<String, String> boundPairs = new HashMap<>();

        for (String currentField : fields) {
            String lastPart = simpleFieldName(currentField);

            if (lastPart.startsWith(pattern.lowSuffix)) {
                boundPairs.putAll(findPairsAtStart(pattern.lowSuffix, pattern.highSuffix, currentField, lastPart, fields));
            } else if (lastPart.endsWith(pattern.lowSuffix)) {
                boundPairs.putAll(findPairsAtEnd(pattern.lowSuffix, pattern.highSuffix, currentField, lastPart, fields));
            }
        }

        return boundPairs;
    }

    private static Map<String, String> findPairsAtEnd(String lowSuffix, String highSuffix, String currentField, String lastPart, Set<String> fields) {
        Map<String, String> boundPairs = new HashMap<>();

        String baseName = lastPart.substring(0, lastPart.length() - lowSuffix.length());
        String pairName = baseName + highSuffix;
        fields.stream().filter(field -> simpleFieldName(field).endsWith(pairName)).findFirst()
                .ifPresent(pairField -> boundPairs.put(currentField, pairField));

        return boundPairs;
    }

    private static Map<String, String> findPairsAtStart(String lowSuffix, String highSuffix, String currentField, String lastPart, Set<String> fields) {
        Map<String, String> boundPairs = new HashMap<>();

        String baseName = lastPart.substring(lowSuffix.length());
        String pairName = highSuffix + baseName;
        fields.stream().filter(field -> simpleFieldName(field).startsWith(pairName)).findFirst()
                .ifPresent(pairField -> boundPairs.put(currentField, pairField));

        return boundPairs;
    }

    private static String simpleFieldName(String fullyQualifiedFieldName) {
        return fullyQualifiedFieldName.substring(fullyQualifiedFieldName.lastIndexOf("#") + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public String description() {
        return "Sends a request where the lower-bound field (e.g., minAmount) is set greater than the upper-bound field (e.g., maxAmount)";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

}