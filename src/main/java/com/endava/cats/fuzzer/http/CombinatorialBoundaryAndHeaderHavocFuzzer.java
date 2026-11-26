package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.Base64BoundaryGenerator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.PayloadFormat;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.HttpHeaders;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Combines handcrafted cross-layer boundary anchors (locale headers, media-type parameter differentials,
 * proxy/precondition headers) with deterministic-random multi-field body mutations (Unicode case/NFKC expansion,
 * SIMD Base64 tails, numeric/integer boundaries, and container/gadget wrapping).
 */
@Singleton
@HttpFuzzer
public class CombinatorialBoundaryAndHeaderHavocFuzzer implements Fuzzer {
    private static final List<String> CASE_SENSITIVE_LOCALES = List.of(
            "tr-TR, az-AZ;q=0.9, el-GR;q=0.8, lt-LT;q=0.7",
            "tr, el;q=0.9, de-DE-u-co-phonebk;q=0.8");
    private static final List<Object> BOUNDARY_NUMBERS = List.of(
            -2147483648L, 2147483647L, 9007199254740993L, -9007199254740993L, 0L, -1L);

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor executor;
    private final RobustnessResponseReporter reporter;
    private final Set<Operation> visited = new HashSet<>();

    public CombinatorialBoundaryAndHeaderHavocFuzzer(SimpleExecutor executor, RobustnessResponseReporter reporter) {
        this.executor = executor;
        this.reporter = reporter;
    }

    @Override
    public void fuzz(FuzzingData data) {
        List<String> presentFields = resolvePresentFields(data);
        if (isApplicableTo(data) && !presentFields.isEmpty() && visited.add(new Operation(data.getMethod(), data.getPath()))) {
            buildProbes(data, presentFields).forEach(probe -> executeProbe(data, probe));
        }
    }

    @Override
    public boolean isApplicableTo(FuzzingData data) {
        return data != null
                && data.getPayloadFormat() == PayloadFormat.JSON
                && data.getMethod() != null
                && HttpMethod.requiresBody(data.getMethod())
                && !JsonUtils.isEmptyPayload(data.getPayload());
    }

    static List<String> resolvePresentFields(FuzzingData data) {
        if (data == null || data.getAllFieldsByHttpMethod() == null || JsonUtils.isEmptyPayload(data.getPayload())) {
            return List.of();
        }
        return data.getAllFieldsByHttpMethod().stream()
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .sorted()
                .toList();
    }

    static List<HavocProbe> buildProbes(FuzzingData data, List<String> presentFields) {
        return List.of(
                buildLocaleAndCaseExpansionProbe(data, presentFields),
                buildContentTypeAndBoundaryProbe(data, presentFields),
                buildProxyHeaderAndBase64Probe(data, presentFields),
                buildMultiFieldHavocProbe(data, presentFields));
    }

    private static HavocProbe buildLocaleAndCaseExpansionProbe(FuzzingData data, List<String> fields) {
        String locale = CASE_SENSITIVE_LOCALES.get(CatsRandom.instance().nextInt(CASE_SENSITIVE_LOCALES.size()));
        String target = selectFieldMatching(data, fields, CatsModelUtils::isStringSchema);
        Schema<?> schema = propertySchema(data, target);
        Object expanding = UnicodeGenerator.generateCaseExpandingPayloads(schema, UnicodeGenerator.getUppercaseExpandingLength()).getLast();
        String payload = CatsUtil.justReplaceField(data.getPayload(), target, "iI\u0130\u0131\u212A" + expanding).json();
        return new HavocProbe(
                "Combine case-sensitive Accept-Language [%s] with Unicode case-expanding payload in [%s].".formatted(locale, target),
                payload,
                replaceHeader(data.getHeaders(), "Accept-Language", locale),
                List.of(RequestTarget.body(target), RequestTarget.header("Accept-Language")));
    }

    private static HavocProbe buildContentTypeAndBoundaryProbe(FuzzingData data, List<String> fields) {
        String baseType = extractBaseMediaType(data.getFirstRequestContentType());
        String ctValue = "%s; charset=utf-8; charset=utf-16be; v=%s".formatted(
                baseType, CatsRandom.alphanumeric(3).toLowerCase(Locale.ROOT));
        String target = selectFieldMatching(data, fields, s -> CatsModelUtils.isNumberSchema(s) || CatsModelUtils.isIntegerSchema(s));
        Object boundaryVal = BOUNDARY_NUMBERS.get(CatsRandom.instance().nextInt(BOUNDARY_NUMBERS.size()));
        String payload = CatsUtil.justReplaceField(data.getPayload(), target, boundaryVal).json();
        return new HavocProbe(
                "Combine differential Content-Type [%s] with numeric boundary [%s] in [%s].".formatted(ctValue, boundaryVal, target),
                payload,
                replaceHeader(data.getHeaders(), HttpHeaders.CONTENT_TYPE, ctValue),
                List.of(RequestTarget.body(target), RequestTarget.header(HttpHeaders.CONTENT_TYPE)));
    }

    private static HavocProbe buildProxyHeaderAndBase64Probe(FuzzingData data, List<String> fields) {
        String target = fields.get(CatsRandom.instance().nextInt(fields.size()));
        String b64Value = Base64BoundaryGenerator.randomBoundaryValue();
        String payload = CatsUtil.justReplaceField(data.getPayload(), target, b64Value).json();
        return new HavocProbe(
                "Combine X-Forwarded-For header with SIMD-stride Base64 tail probe in [%s].".formatted(target),
                payload,
                replaceHeader(data.getHeaders(), "X-Forwarded-For", "127.0.0.1, ::1, 0.0.0.0"),
                List.of(RequestTarget.body(target), RequestTarget.header("X-Forwarded-For")));
    }

    private static HavocProbe buildMultiFieldHavocProbe(FuzzingData data, List<String> fields) {
        int count = Math.min(fields.size(), 2 + CatsRandom.instance().nextInt(2));
        Set<String> chosen = new LinkedHashSet<>();
        String payload = data.getPayload();
        for (int i = 0; i < count; i++) {
            String field = fields.get((i + CatsRandom.instance().nextInt(fields.size())) % fields.size());
            if (!JsonUtils.isFieldInJson(payload, field) || overlapsAny(chosen, field)) {
                continue;
            }
            chosen.add(field);
            payload = CatsUtil.justReplaceField(payload, field, randomHavocValue(propertySchema(data, field))).json();
        }
        List<RequestTarget> targets = new ArrayList<>(chosen.stream().map(RequestTarget::body).toList());
        targets.add(RequestTarget.header("If-None-Match"));
        return new HavocProbe(
                "Combine precondition header with multi-field boundary/normalization mutations in %s.".formatted(chosen),
                payload,
                replaceHeader(data.getHeaders(), "If-None-Match", "W/\"cats-" + CatsRandom.alphanumeric(4) + "\""),
                List.copyOf(targets));
    }

    private static boolean overlapsAny(Set<String> chosen, String candidate) {
        return chosen.stream().anyMatch(existing -> isAncestorOrDescendant(existing, candidate));
    }

    private static boolean isAncestorOrDescendant(String first, String second) {
        return first.startsWith(second + "#") || second.startsWith(first + "#")
                || first.startsWith(second + "[") || second.startsWith(first + "[");
    }

    private static Object randomHavocValue(Schema<?> schema) {
        return switch (CatsRandom.instance().nextInt(4)) {
            case 0 -> UnicodeGenerator.generateNormalizationDifferentialPayloads(schema).getFirst();
            case 1 -> Base64BoundaryGenerator.randomBoundaryValue();
            case 2 -> BOUNDARY_NUMBERS.get(CatsRandom.instance().nextInt(BOUNDARY_NUMBERS.size()));
            default -> Map.of("$ne", "", "value", "\u0130\u00DF\uFDFA");
        };
    }

    private static String selectFieldMatching(FuzzingData data, List<String> fields, Predicate<Schema<?>> predicate) {
        List<String> matching = fields.stream()
                .filter(field -> predicate.test(propertySchema(data, field)))
                .toList();
        List<String> pool = matching.isEmpty() ? fields : matching;
        return pool.get(CatsRandom.instance().nextInt(pool.size()));
    }

    private static Schema<?> propertySchema(FuzzingData data, String field) {
        Map<String, Schema> types = data.getRequestPropertyTypes();
        return types == null ? null : types.get(field);
    }

    private static String extractBaseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/json";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim();
    }

    private static Collection<CatsHeader> replaceHeader(Collection<CatsHeader> existing, String name, String value) {
        Stream<CatsHeader> retained = Optional.ofNullable(existing).orElse(Set.of()).stream()
                .filter(header -> !name.equalsIgnoreCase(header.getName()));
        return Stream.concat(retained, Stream.of(CatsHeader.from(name, value, true, null))).toList();
    }

    private void executeProbe(FuzzingData data, HavocProbe probe) {
        executor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .fuzzer(this)
                .logger(logger)
                .scenario(probe.scenario())
                .payload(probe.payload())
                .headers(probe.headers())
                .mutationTargets(probe.targets())
                .responseProcessor((response, context) -> reporter.report(logger, context, response))
                .expectedResponseCode(RobustnessResponseReporter.expectedResponses())
                .matchResponseResult(false)
                .matchResponseContentType(false)
                .build());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.OPTIONS);
    }

    @Override
    public String description() {
        return "combines locale, Content-Type, and proxy headers with multi-field Unicode, Base64, and numeric boundary mutations";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    record HavocProbe(String scenario, String payload, Collection<CatsHeader> headers, List<RequestTarget> targets) {
    }

    private record Operation(HttpMethod method, String path) {
    }
}
