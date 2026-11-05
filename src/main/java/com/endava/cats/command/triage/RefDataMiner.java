package com.endava.cats.command.triage;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.util.JsonUtils;
import com.google.common.base.Splitter;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Mines working path-parameter values and created resource identifiers from {@code 2XX} exchanges into a
 * {@code --refData} file. Only real contract path templates are emitted, values sent by path-mutating fuzzers or
 * accepted negative tests are ignored, and identifiers later removed by a successful {@code DELETE} are excluded.
 */
final class RefDataMiner {
    private static final String HEADER = """
            # Auto-mined from 2XX exchanges by `cats triage`
            # Pinned path parameters disable runtime resource reuse for that parameter; keep only the entries you need.
            """;
    private static final Set<String> GENERIC_ID_KEYS = Set.of("id", "uuid", "guid");

    private final Set<String> knownPaths;
    private final Map<String, Map<String, Object>> mined = new LinkedHashMap<>();
    private final Set<String> deletedValues = new LinkedHashSet<>();

    RefDataMiner(Set<String> knownPaths) {
        this.knownPaths = Set.copyOf(knownPaths);
    }

    void observe(TriageTestCase testCase) {
        if (!ResponseCodeFamily.is2xxCode(testCase.statusCode()) || testCase.expected().expectsOnly4xx()) {
            return;
        }
        Map<String, String> pathParams = unmutatedPathParams(testCase);
        if ("DELETE".equals(testCase.method())) {
            pathParams.forEach((name, value) -> deletedValues.add(deletionKey(name, value)));
            return;
        }
        pathParams.forEach((name, value) -> store(testCase.path(), name, value));
        if ("POST".equals(testCase.method())) {
            mineCreatedIdentifiers(testCase);
        }
    }

    /**
     * Returns mined values per contract path, excluding identifiers deleted later in the run.
     */
    Map<String, Map<String, Object>> mined() {
        Map<String, Map<String, Object>> live = new LinkedHashMap<>();
        mined.forEach((path, values) -> values.entrySet().stream()
                .filter(this::isNotDeleted)
                .forEach(entry -> live.computeIfAbsent(path, _ -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue())));
        return live;
    }

    String renderYaml() {
        Map<String, Map<String, Object>> live = mined();
        if (live.isEmpty()) {
            return "# No 2XX path parameters or resource IDs mined from report\n";
        }
        StringBuilder yaml = new StringBuilder(HEADER);
        live.forEach((path, values) -> appendPath(yaml, path, values));
        return yaml.toString();
    }

    private static void appendPath(StringBuilder yaml, String path, Map<String, Object> values) {
        yaml.append(JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(path)).append(":\n");
        values.forEach((name, value) -> yaml.append("  ")
                .append(FieldNames.isSimpleYamlKey(name) ? name : JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(name))
                .append(": ").append(JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(value)).append('\n'));
    }

    private boolean isNotDeleted(Map.Entry<String, Object> entry) {
        return !deletedValues.contains(deletionKey(entry.getKey(), String.valueOf(entry.getValue())));
    }

    private void mineCreatedIdentifiers(TriageTestCase testCase) {
        testCase.responseJson().filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .ifPresent(body -> body.entrySet().forEach(entry -> mineCreatedIdentifier(testCase.path(), entry.getKey(), entry.getValue())));
    }

    private static Map<String, String> unmutatedPathParams(TriageTestCase testCase) {
        Map<String, String> params = new LinkedHashMap<>(extractPathParams(testCase.path(), testCase.url()));
        params.keySet().removeIf(testCase::isPathMutated);
        return params;
    }

    /**
     * Aligns contract segments with the tail of the concrete URL path (tolerating a server base path).
     */
    static Map<String, String> extractPathParams(String contractPath, String url) {
        if (!contractPath.contains("{") || StringUtils.isBlank(url)) {
            return Map.of();
        }
        List<String> contractSegments = Splitter.on('/').splitToList(contractPath);
        List<String> actualSegments = urlPathSegments(url);
        int offset = actualSegments.size() - contractSegments.size();
        if (offset < 0) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        IntStream.range(0, contractSegments.size())
                .filter(i -> isTemplate(contractSegments.get(i)) && StringUtils.isNotBlank(actualSegments.get(i + offset)))
                .forEach(i -> params.put(templateName(contractSegments.get(i)), actualSegments.get(i + offset)));
        return params;
    }

    private static List<String> urlPathSegments(String url) {
        try {
            return Optional.ofNullable(URI.create(url).getPath()).map(Splitter.on('/')::splitToList).orElse(List.of());
        } catch (IllegalArgumentException _) {
            return List.of();
        }
    }

    private void mineCreatedIdentifier(String collectionPath, String key, JsonElement value) {
        if (!isIdKey(key) || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) {
            return;
        }
        List<String> itemParams = knownPaths.stream()
                .filter(path -> isDirectItemPath(collectionPath, path))
                .map(path -> templateName(path.substring(collectionPath.length() + 1)))
                .toList();
        itemParams.stream()
                .filter(param -> param.equalsIgnoreCase(key) || (GENERIC_ID_KEYS.contains(key.toLowerCase(Locale.ROOT)) && itemParams.size() == 1))
                .findFirst()
                .ifPresent(param -> store(collectionPath + "/{" + param + "}", param, primitiveValue(value.getAsJsonPrimitive())));
    }

    private static boolean isDirectItemPath(String collectionPath, String candidate) {
        String prefix = collectionPath + "/";
        return candidate.startsWith(prefix) && isTemplate(candidate.substring(prefix.length()));
    }

    private void store(String path, String name, Object value) {
        mined.computeIfAbsent(path, _ -> new LinkedHashMap<>()).putIfAbsent(name, value);
    }

    static boolean isIdKey(String key) {
        return GENERIC_ID_KEYS.contains(key.toLowerCase(Locale.ROOT))
                || key.endsWith("Id") || key.endsWith("ID") || key.toLowerCase(Locale.ROOT).endsWith("_id");
    }

    private static boolean isTemplate(String segment) {
        return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}") && !segment.contains("/");
    }

    private static String templateName(String segment) {
        return segment.substring(1, segment.length() - 1);
    }

    private static Object primitiveValue(JsonPrimitive primitive) {
        return primitive.isNumber() ? primitive.getAsNumber() : primitive.getAsString();
    }

    private static String deletionKey(String name, String value) {
        return name + "=" + value;
    }
}
