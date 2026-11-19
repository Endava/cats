package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exercises regular-expression compilers, {@code Regexp#to_s} serializers, and case-insensitive
 * backreference/anchor matchers on both pattern-consuming fields and OpenAPI {@code pattern}-constrained fields.
 */
@Singleton
@FieldFuzzer
public class RegexEngineAndPatternDifferentialFieldsFuzzer implements Fuzzer {
    private static final Set<String> PATTERN_CONSUMER_NAMES = Set.of(
            "regex", "regexp", "pattern", "filter", "query", "search", "match", "rule", "expression");
    private static final Map<Character, String> CASE_FOLD_EQUIVALENTS = Map.of(
            'i', "\u0130", 'I', "\u0131",
            's', "\u017F", 'S', "\u00DF",
            'k', "\u212A", 'K', "\u212A",
            'f', "\uFB00");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RegexEngineAndPatternDifferentialFieldsFuzzer.class);
    private final FieldsIteratorExecutor executor;

    public RegexEngineAndPatternDifferentialFieldsFuzzer(FieldsIteratorExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        executor.execute(FieldsIteratorExecutorContext.builder()
                .scenario("Send regex compiler/serialization edge patterns and case-folded/multiline pattern differential probes.")
                .fuzzingData(data)
                .logger(logger)
                .fuzzer(this)
                .fuzzingStrategy(FuzzingStrategy.replace())
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                .fieldFilter(field -> isEligibleField(data, field))
                .fuzzValueProducer((schema, field) -> buildProbes(data, schema, field))
                .simpleReplaceField(true)
                .skipMessage("Field is neither a regex/pattern consumer nor constrained by an OpenAPI pattern.")
                .build());
    }

    static boolean isEligibleField(FuzzingData data, String field) {
        Map<String, Schema> types = data.getRequestPropertyTypes();
        Schema<?> schema = types == null ? null : types.get(field);
        return CatsModelUtils.isStringSchema(schema)
                && !CatsModelUtils.isEnumSchema(schema)
                && JsonUtils.isFieldInJson(data.getPayload(), field)
                && (isPatternConsumer(schema, field) || hasPatternConstraint(schema));
    }

    static boolean isPatternConsumer(Schema<?> schema, String field) {
        String format = schema != null ? schema.getFormat() : null;
        return "regex".equalsIgnoreCase(format)
                || "regexp".equalsIgnoreCase(format)
                || PATTERN_CONSUMER_NAMES.contains(WordUtils.normalizeLeaf(field));
    }

    private static boolean hasPatternConstraint(Schema<?> schema) {
        return schema != null && StringUtils.isNotBlank(schema.getPattern());
    }

    static List<Object> buildProbes(FuzzingData data, Schema<?> schema, String field) {
        List<Object> probes = new ArrayList<>();
        if (isPatternConsumer(schema, field)) {
            probes.addAll(compilerAndStringifyProbes());
        }
        if (hasPatternConstraint(schema)) {
            String baseline = resolveBaselineValue(data, field);
            probes.add(buildCaseFoldedBackrefTwin(baseline));
            probes.add(baseline + "\n" + CatsRandom.alphanumeric(4) + "\u0130\u00DF");
        }
        return List.copyOf(probes);
    }

    private static List<Object> compilerAndStringifyProbes() {
        String tag = CatsRandom.alphanumeric(3).toLowerCase(Locale.ROOT);
        int repeatBound = 63 + CatsRandom.instance().nextInt(3);
        return List.of(
                "(?i)(?<k%s>[a-z\\u0130\\u00DF]+)\\k<k%s>{1,%d}".formatted(tag, tag, repeatBound),
                "(?i-m:(?:(?>[a-z0-9_]+)|(?<g%s>\\w+))\\k<g%s>)".formatted(tag, tag),
                "[[a-z0-9]&&[^%s]]{0,255}(?:(\\w+)\\1)?".formatted(tag));
    }

    private static String resolveBaselineValue(FuzzingData data, String field) {
        Object raw = JsonUtils.getVariableFromJson(data.getPayload(), field);
        String text = raw == null ? "" : String.valueOf(raw);
        return text.isBlank() || JsonUtils.isNotSet(text) ? "skifSKIF" : text;
    }

    static String buildCaseFoldedBackrefTwin(String baseline) {
        String folded = foldCharacters(baseline);
        return folded.equals(baseline) ? baseline + "s\u017Fi\u0130k\u212A" : folded;
    }

    private static String foldCharacters(String baseline) {
        StringBuilder builder = new StringBuilder(baseline.length());
        for (int i = 0; i < baseline.length(); i++) {
            char ch = baseline.charAt(i);
            builder.append(CASE_FOLD_EQUIVALENTS.getOrDefault(ch, String.valueOf(ch)));
        }
        return builder.toString();
    }

    @Override
    public String description() {
        return "checks regex-consuming and pattern-constrained fields with compiler, Regexp#to_s, case-folding backreference, and multiline anchor probes";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
