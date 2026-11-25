package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.ReportingArguments.RuntimeSensitiveField;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestProvenance;
import com.endava.cats.model.RequestTarget;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SensitiveDataPolicy {
    public static final String REDACTED = "[REDACTED]";
    public static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\$([A-Za-z_]\\w*)");
    private static final int MIN_SUBSTRING_SECRET_LENGTH = 4;
    private static final Set<String> SENSITIVE_HEADER_PARTS = Set.of(
            "authorization", "authorisation", "cookie", "token", "jwt", "apikey", "secret", "password", "credential", "appkey", "appid");
    private static final Set<String> SENSITIVE_QUERY_NAMES = Set.of(
            "accesstoken", "idtoken", "authtoken", "apikey", "jwt", "password", "secret", "signature", "sig", "token");

    private SensitiveDataPolicy() {
    }

    public static boolean isExplicitlyConfigured(String candidate, Set<String> configuredNames) {
        return candidate != null && Optional.ofNullable(configuredNames).orElseGet(Set::of).stream()
                .filter(Objects::nonNull)
                .anyMatch(configured -> configured.trim().equalsIgnoreCase(candidate));
    }

    public static boolean isSensitiveHeader(String header, Set<String> configuredHeaders, boolean showSecrets) {
        if (header == null) {
            return false;
        }
        if (isExplicitlyConfigured(header, configuredHeaders)) {
            return true;
        }
        String normalized = normalize(header);
        return !showSecrets && SENSITIVE_HEADER_PARTS.stream().anyMatch(normalized::contains);
    }

    public static boolean isSensitiveQueryParam(String queryParam, Set<String> configuredQueryParams, boolean showSecrets) {
        if (queryParam == null) {
            return false;
        }
        boolean explicitlyConfigured = isExplicitlyConfigured(queryParam, configuredQueryParams);
        String normalized = normalize(queryParam);
        return explicitlyConfigured || (!showSecrets && SENSITIVE_QUERY_NAMES.contains(normalized));
    }

    public static String headerEnvironmentVariable(String header) {
        return header.replaceAll("[^A-Za-z0-9]", "");
    }

    public static String queryEnvironmentVariable(String queryParam) {
        String variable = queryParam.replaceAll("\\W", "_").toUpperCase(Locale.ROOT);
        return variable.isEmpty() || Character.isDigit(variable.charAt(0)) ? "_" + variable : variable;
    }

    public static String fieldEnvironmentVariable(String fieldPath) {
        String variable = normalizeFieldPath(fieldPath).replace('#', '_').replaceAll("\\W", "");
        return variable.isEmpty() || Character.isDigit(variable.charAt(0)) ? "_" + variable : variable;
    }

    public static String normalizeFieldPath(String path) {
        if (path == null) {
            return "";
        }
        String cleaned = path.trim()
                .replaceFirst("^\\$(?:\\[\\*])?[.#]?", "")
                .replaceAll("\\[(?:\\*|\\d+)]", "")
                .replace('[', '#')
                .replace("]", "")
                .replace('.', '#');
        return Arrays.stream(cleaned.split("#"))
                .filter(segment -> !segment.isEmpty() && !segment.chars().allMatch(Character::isDigit))
                .collect(Collectors.joining("#"));
    }

    public static Optional<String> extractEnvVariableName(String rawValue) {
        return extractEnvVariableNames(rawValue).stream().findFirst();
    }

    public static List<String> extractEnvVariableNames(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = ENV_VAR_PATTERN.matcher(rawValue);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    public static Set<String> openApiQueryApiKeyNames(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getComponents() == null || openAPI.getComponents().getSecuritySchemes() == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        openAPI.getComponents().getSecuritySchemes().values().stream()
                .filter(scheme -> SecurityScheme.Type.APIKEY.equals(scheme.getType()))
                .filter(scheme -> SecurityScheme.In.QUERY.equals(scheme.getIn()))
                .map(SecurityScheme::getName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    public static String placeholder(String environmentVariable) {
        return "$$" + environmentVariable;
    }

    public static List<KeyValuePair<String, Object>> maskHeadersForDisplay(List<? extends KeyValuePair<String, ?>> headers,
                                                                           ReportingArguments reportingArguments) {
        List<KeyValuePair<String, Object>> result = new ArrayList<>();
        for (KeyValuePair<String, ?> header : Optional.ofNullable(headers).orElseGet(List::of)) {
            Object value = reportingArguments.shouldMaskHeader(header.getKey())
                    ? placeholder(headerEnvironmentVariable(header.getKey())) : header.getValue();
            result.add(new KeyValuePair<>(header.getKey(), value));
        }
        return result;
    }

    public static Map<String, Object> maskHeadersForDisplay(Map<String, ?> headers, ReportingArguments reportingArguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional.ofNullable(headers).orElseGet(Map::of).forEach((name, value) -> result.put(name,
                reportingArguments.shouldMaskHeader(name) ? placeholder(headerEnvironmentVariable(name)) : value));
        return result;
    }

    public static void sanitize(CatsTestCase testCase, ReportingArguments reportingArguments) {
        CatsRequest request = testCase.getRequest();
        HeaderPresentation headers = new HeaderPresentation(reportingArguments);
        if (request != null) {
            sanitizeRequestHeaders(testCase, request, headers, reportingArguments);
            sanitizeRequestPayload(testCase, request, reportingArguments);
            Set<String> mutatedQuery = mutatedQueryParams(testCase);
            request.setUrl(maskUrl(request.getUrl(), reportingArguments, mutatedQuery, true));
            testCase.setFullRequestPath(maskUrl(testCase.getFullRequestPath(), reportingArguments, mutatedQuery, true));
            testCase.setPath(maskUrl(testCase.getPath(), reportingArguments, mutatedQuery, true));
            testCase.setContractPath(maskUrl(testCase.getContractPath(), reportingArguments, mutatedQuery, true));
        }
        if (testCase.getResponse() != null) {
            sanitizeResponseHeaders(testCase.getResponse(), reportingArguments);
            sanitizeResponsePayload(testCase, reportingArguments);
        }
    }

    private static void sanitizeRequestHeaders(CatsTestCase testCase, CatsRequest request,
                                               HeaderPresentation headers, ReportingArguments reportingArguments) {
        for (KeyValuePair<String, Object> header : Optional.ofNullable(request.getHeaders()).orElseGet(List::of)) {
            if (headers.shouldMaskRequest(testCase, header)) {
                String variable = headerEnvironmentVariable(header.getKey());
                reportingArguments.registerSensitiveHeaders(Set.of(header.getKey()));
                reportingArguments.registerReplayEnvironmentVariable(variable);
                header.setValue(placeholder(variable));
            }
        }
    }

    private static void sanitizeResponseHeaders(CatsResponse response, ReportingArguments reportingArguments) {
        for (KeyValuePair<String, String> header : Optional.ofNullable(response.getHeaders()).orElseGet(List::of)) {
            if (reportingArguments.shouldMaskHeader(header.getKey())) {
                reportingArguments.registerSensitiveHeaders(Set.of(header.getKey()));
                header.setValue(REDACTED);
            }
        }
    }

    public static String maskPayloadForDisplay(String payload, ReportingArguments reportingArguments) {
        return maskPayload(payload, reportingArguments, Set.of(), true, false);
    }

    public static String maskResponseBodyForDisplay(String responseBody, ReportingArguments reportingArguments) {
        return maskJsonResponseString(responseBody, reportingArguments);
    }

    public static Object maskFieldValueForDisplay(String fieldPath, Object value, ReportingArguments reportingArguments) {
        if (value == null || !hasFieldMaskingRules(reportingArguments)) {
            return value;
        }
        String normalizedPath = normalizeFieldPath(fieldPath);
        String leafKey = leafKeyOf(normalizedPath);
        return resolveFieldMaskReplacement(leafKey, normalizedPath, String.valueOf(value),
                reportingArguments, Set.of(), true, false)
                .<Object>map(masked -> masked)
                .orElse(value);
    }

    public static Map<String, Object> maskFieldsForDisplay(Map<String, ?> fields, ReportingArguments reportingArguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional.ofNullable(fields).orElseGet(Map::of)
                .forEach((key, value) -> result.put(key, maskFieldValueForDisplay(key, value, reportingArguments)));
        return result;
    }

    public static String maskRequestPayload(CatsTestCase testCase, ReportingArguments reportingArguments) {
        String payload = Optional.ofNullable(testCase).map(CatsTestCase::getRequest).map(CatsRequest::getPayload).orElse(null);
        return maskPayload(payload, reportingArguments, mutatedBodyFields(testCase), true, false);
    }

    public static String maskResponsePayload(CatsResponse response, ReportingArguments reportingArguments) {
        if (response == null) {
            return null;
        }
        String original = response.getBody();
        if (StringUtils.isBlank(original) && response.getJsonBody() != null) {
            original = response.getJsonBody().toString();
        }
        String masked = maskJsonResponseString(original, reportingArguments);
        return Objects.equals(original, masked) ? response.getBody() : masked;
    }

    private static String maskJsonResponseString(String original, ReportingArguments reportingArguments) {
        if (StringUtils.isBlank(original) || !hasFieldMaskingRules(reportingArguments)) {
            return original;
        }
        try {
            JsonElement parsed = JsonParser.parseString(original);
            if (maskJsonElement(parsed, "", reportingArguments, Set.of(), false, false)) {
                return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(parsed);
            }
        } catch (JsonSyntaxException _) {
            // Non-JSON response bodies remain unchanged.
        }
        return original;
    }

    private static void sanitizeRequestPayload(CatsTestCase testCase, CatsRequest request, ReportingArguments reportingArguments) {
        String original = request.getPayload();
        String masked = maskPayload(original, reportingArguments, mutatedBodyFields(testCase), true, true);
        if (!Objects.equals(original, masked)) {
            request.setPayload(masked);
            request.setEncodedBody(null);
        }
    }

    private static Set<String> mutatedBodyFields(CatsTestCase testCase) {
        return Optional.ofNullable(testCase)
                .map(CatsTestCase::getRequestProvenance)
                .map(RequestProvenance::getMutationTargets)
                .orElse(List.of()).stream()
                .filter(target -> target.location() == RequestTarget.Location.BODY)
                .map(target -> normalizeFieldPath(target.name()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> mutatedQueryParams(CatsTestCase testCase) {
        return Optional.ofNullable(testCase)
                .map(CatsTestCase::getRequestProvenance)
                .map(RequestProvenance::getMutationTargets)
                .orElse(List.of()).stream()
                .filter(target -> target.location() == RequestTarget.Location.QUERY)
                .map(RequestTarget::name)
                .filter(StringUtils::isNotBlank)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void sanitizeResponsePayload(CatsTestCase testCase, ReportingArguments reportingArguments) {
        CatsResponse response = testCase.getResponse();
        String original = response.getBody();
        if (StringUtils.isBlank(original) && response.getJsonBody() != null) {
            original = response.getJsonBody().toString();
        }
        if (StringUtils.isBlank(original) || !hasFieldMaskingRules(reportingArguments)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(original);
            if (maskJsonElement(parsed, "", reportingArguments, Set.of(), false, false)) {
                testCase.setResponse(response.withSanitizedBody(JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(parsed), parsed));
            }
        } catch (JsonSyntaxException _) {
            // Non-JSON response bodies remain unchanged.
        }
    }

    private static String maskPayload(String payload, ReportingArguments reportingArguments,
                                      Set<String> mutatedFields, boolean requestSide, boolean registerReplayVars) {
        if (StringUtils.isBlank(payload) || !hasFieldMaskingRules(reportingArguments)) {
            return payload;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return maskJsonPayloadString(payload, reportingArguments, mutatedFields, requestSide, registerReplayVars);
        }
        if (requestSide && looksLikeFormUrlEncoded(trimmed)) {
            return maskFormUrlEncodedPayload(payload, reportingArguments, mutatedFields, registerReplayVars);
        }
        return payload;
    }

    private static String maskJsonPayloadString(String payload, ReportingArguments reportingArguments,
                                                Set<String> mutatedFields, boolean requestSide, boolean registerReplayVars) {
        try {
            JsonElement parsed = JsonParser.parseString(payload);
            if (maskJsonElement(parsed, "", reportingArguments, mutatedFields, requestSide, registerReplayVars)) {
                return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(parsed);
            }
        } catch (JsonSyntaxException _) {
            // Malformed JSON payloads sent by fuzzers remain unchanged.
        }
        return payload;
    }

    private static boolean maskJsonElement(JsonElement element, String currentPath,
                                           ReportingArguments reportingArguments, Set<String> mutatedFields,
                                           boolean requestSide, boolean registerReplayVars) {
        if (element instanceof JsonObject obj) {
            return maskJsonObject(obj, currentPath, reportingArguments, mutatedFields, requestSide, registerReplayVars);
        }
        if (element instanceof JsonArray arr) {
            return maskJsonArray("", currentPath, arr, reportingArguments, mutatedFields, requestSide, registerReplayVars);
        }
        return false;
    }

    private static boolean maskJsonArray(String leafKey, String fieldPath, JsonArray arr,
                                         ReportingArguments reportingArguments, Set<String> mutatedFields,
                                         boolean requestSide, boolean registerReplayVars) {
        boolean modified = false;
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item instanceof JsonObject childObj) {
                modified |= maskJsonObject(childObj, fieldPath, reportingArguments, mutatedFields, requestSide, registerReplayVars);
            } else if (item instanceof JsonArray childArr) {
                modified |= maskJsonArray(leafKey, fieldPath, childArr, reportingArguments, mutatedFields, requestSide, registerReplayVars);
            } else if (item instanceof JsonPrimitive primitive && !leafKey.isEmpty()) {
                Optional<String> replacement = resolveFieldMaskReplacement(
                        leafKey, fieldPath, primitive.getAsString(), reportingArguments, mutatedFields, requestSide, registerReplayVars);
                if (replacement.isPresent()) {
                    arr.set(i, new JsonPrimitive(replacement.get()));
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static boolean maskJsonObject(JsonObject obj, String parentPath,
                                          ReportingArguments reportingArguments, Set<String> mutatedFields,
                                          boolean requestSide, boolean registerReplayVars) {
        boolean modified = false;
        for (String key : new ArrayList<>(obj.keySet())) {
            String fieldPath = parentPath.isEmpty() ? key : parentPath + "#" + key;
            JsonElement child = obj.get(key);
            if (child instanceof JsonObject childObj) {
                modified |= maskJsonObject(childObj, fieldPath, reportingArguments, mutatedFields, requestSide, registerReplayVars);
            } else if (child instanceof JsonArray childArr) {
                modified |= maskJsonArray(key, fieldPath, childArr, reportingArguments, mutatedFields, requestSide, registerReplayVars);
            } else if (child instanceof JsonPrimitive primitive) {
                Optional<String> replacement = resolveFieldMaskReplacement(
                        key, fieldPath, primitive.getAsString(), reportingArguments, mutatedFields, requestSide, registerReplayVars);
                if (replacement.isPresent()) {
                    obj.add(key, new JsonPrimitive(replacement.get()));
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static Optional<String> resolveFieldMaskReplacement(String leafKey, String fieldPath, String currentValue,
                                                                ReportingArguments reportingArguments,
                                                                Set<String> mutatedFields,
                                                                boolean requestSide, boolean registerReplayVars) {
        if (REDACTED.equals(currentValue) || (currentValue != null && ENV_VAR_PATTERN.matcher(currentValue).find())) {
            return Optional.empty();
        }
        boolean mutated = requestSide && isMutatedField(fieldPath, mutatedFields);
        Optional<RuntimeSensitiveField> runtimeMatch = reportingArguments.isShowSecrets()
                ? Optional.empty()
                : findMatchingRuntimeField(leafKey, fieldPath, currentValue, reportingArguments.getRuntimeSensitiveFields());
        if (runtimeMatch.isPresent()) {
            return Optional.of(applyRuntimeReplacement(runtimeMatch.get(), currentValue, reportingArguments, requestSide, registerReplayVars));
        }
        if (mutated) {
            return Optional.empty();
        }
        return findMatchingMaskEntry(fieldPath, reportingArguments.getExplicitFieldMasks())
                .map(matched -> replacementForMatchedField(leafKey, fieldPath, currentValue, matched,
                        reportingArguments, requestSide, registerReplayVars));
    }

    private static String applyRuntimeReplacement(RuntimeSensitiveField runtime, String currentValue,
                                                  ReportingArguments reportingArguments,
                                                  boolean requestSide, boolean registerReplayVars) {
        if (!requestSide) {
            return REDACTED;
        }
        if (registerReplayVars) {
            extractEnvVariableNames(runtime.rawTemplate()).forEach(reportingArguments::registerReplayEnvironmentVariable);
        }
        return currentValue.replace(runtime.secretValue(), runtime.rawTemplate());
    }

    private static boolean isMutatedField(String fieldPath, Set<String> mutatedFields) {
        String normalizedPath = normalizeFieldPath(fieldPath);
        return mutatedFields.stream().anyMatch(mutated -> normalizedPath.equalsIgnoreCase(mutated)
                || normalizedPath.toLowerCase(Locale.ROOT).startsWith(mutated.toLowerCase(Locale.ROOT) + "#"));
    }

    private static String replacementForMatchedField(String leafKey, String fieldPath, String currentValue,
                                                     String matchedMask, ReportingArguments reportingArguments,
                                                     boolean requestSide, boolean registerReplayVars) {
        if (!requestSide) {
            return REDACTED;
        }
        Optional<RuntimeSensitiveField> runtime = findMatchingRuntimeField(
                leafKey, fieldPath, currentValue, reportingArguments.getRuntimeSensitiveFields());
        if (runtime.isPresent()) {
            return applyRuntimeReplacement(runtime.get(), currentValue, reportingArguments, true, registerReplayVars);
        }
        String variable = variableForMaskEntry(leafKey, fieldPath, matchedMask);
        if (registerReplayVars) {
            reportingArguments.registerReplayEnvironmentVariable(variable);
        }
        return placeholder(variable);
    }

    private static String variableForMaskEntry(String leafKey, String fieldPath, String matchedMask) {
        String normalizedMask = normalizeFieldPath(matchedMask);
        boolean useFullPath = normalizedMask.contains("#") || !leafKey.equalsIgnoreCase(normalizedMask);
        return fieldEnvironmentVariable(useFullPath ? fieldPath : leafKey);
    }

    private static Optional<String> findMatchingMaskEntry(String fieldPath, Set<String> configuredMasks) {
        if (configuredMasks == null || configuredMasks.isEmpty()) {
            return Optional.empty();
        }
        String normalizedPath = normalizeFieldPath(fieldPath);
        for (String mask : configuredMasks) {
            if (matchesFieldMask(normalizedPath, mask)) {
                return Optional.of(mask);
            }
        }
        return Optional.empty();
    }

    public static boolean matchesFieldMask(String normalizedPath, String rawMask) {
        if (StringUtils.isBlank(rawMask) || StringUtils.isBlank(normalizedPath)) {
            return false;
        }
        String normalizedMask = normalizeFieldPath(rawMask);
        if (normalizedMask.isEmpty()) {
            return false;
        }
        String boundedPath = "#" + normalizedPath.toLowerCase(Locale.ROOT) + "#";
        String boundedMask = "#" + normalizedMask.toLowerCase(Locale.ROOT) + "#";
        return boundedPath.contains(boundedMask);
    }

    private static boolean matchesRuntimeFieldPath(String leafKey, String normalizedPath, String candidatePath) {
        if (StringUtils.isBlank(candidatePath)) {
            return false;
        }
        if (candidatePath.contains("#")) {
            return normalizedPath.equalsIgnoreCase(candidatePath)
                    || normalizedPath.toLowerCase(Locale.ROOT).endsWith("#" + candidatePath.toLowerCase(Locale.ROOT));
        }
        return leafKey.equalsIgnoreCase(candidatePath);
    }

    private static Optional<RuntimeSensitiveField> findMatchingRuntimeField(
            String leafKey, String fieldPath, String currentValue,
            List<RuntimeSensitiveField> runtimeFields) {
        if (runtimeFields.isEmpty()) {
            return Optional.empty();
        }
        String normalizedPath = normalizeFieldPath(fieldPath);
        for (RuntimeSensitiveField candidate : runtimeFields) {
            if (matchesRuntimeFieldPath(leafKey, normalizedPath, candidate.fieldPath())
                    && matchesRuntimeSecret(currentValue, candidate.secretValue())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesRuntimeSecret(String currentValue, String secretValue) {
        if (StringUtils.isBlank(currentValue) || StringUtils.isBlank(secretValue)) {
            return false;
        }
        return currentValue.equals(secretValue)
                || (secretValue.length() >= MIN_SUBSTRING_SECRET_LENGTH && currentValue.contains(secretValue));
    }

    private static String maskFormUrlEncodedPayload(String payload, ReportingArguments reportingArguments,
                                                    Set<String> mutatedFields, boolean registerReplayVars) {
        String[] pairs = payload.split("&", -1);
        List<String> updated = new ArrayList<>(pairs.length);
        boolean modified = false;
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String rawKey = pair.substring(0, eq);
                String decodedKey = decodeFormComponent(rawKey);
                String normalizedPath = normalizeFieldPath(decodedKey);
                String leafKey = leafKeyOf(normalizedPath);
                String decodedVal = decodeFormComponent(pair.substring(eq + 1));
                Optional<String> replacement = resolveFieldMaskReplacement(
                        leafKey, normalizedPath, decodedVal, reportingArguments, mutatedFields, true, registerReplayVars);
                if (replacement.isPresent()) {
                    updated.add(rawKey + "=" + replacement.get());
                    modified = true;
                    continue;
                }
            }
            updated.add(pair);
        }
        return modified ? String.join("&", updated) : payload;
    }

    public static String resolvePayloadPlaceholders(String payload, UnaryOperator<String> envLookup) {
        if (StringUtils.isBlank(payload)) {
            return payload;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return resolveJsonPlaceholders(payload, envLookup);
        }
        if (looksLikeFormUrlEncoded(trimmed)) {
            return resolveFormPlaceholders(payload, envLookup);
        }
        return replaceEnvTokens(payload, envLookup, false);
    }

    private static String resolveJsonPlaceholders(String payload, UnaryOperator<String> envLookup) {
        try {
            JsonElement parsed = JsonParser.parseString(payload);
            if (resolveJsonElementPlaceholders(parsed, envLookup)) {
                return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(parsed);
            }
            return payload;
        } catch (JsonSyntaxException _) {
            return replaceEnvTokens(payload, envLookup, false);
        }
    }

    private static boolean resolveJsonElementPlaceholders(JsonElement element, UnaryOperator<String> envLookup) {
        boolean modified = false;
        switch (element) {
            case JsonObject obj -> {
                for (String key : new ArrayList<>(obj.keySet())) {
                    JsonElement child = obj.get(key);
                    switch (child) {
                        case JsonPrimitive primitive when primitive.isString() ->
                                modified |= resolveJsonStringPlaceholder(primitive, envLookup, replacement -> obj.add(key, replacement));
                        case null, default -> modified |= resolveJsonElementPlaceholders(child, envLookup);
                    }
                }
            }
            case JsonArray arr -> {
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement item = arr.get(i);
                    int index = i;
                    switch (item) {
                        case JsonPrimitive primitive when primitive.isString() ->
                                modified |= resolveJsonStringPlaceholder(primitive, envLookup, replacement -> arr.set(index, replacement));
                        case null, default -> modified |= resolveJsonElementPlaceholders(item, envLookup);
                    }
                }
            }
            case null, default -> {
                // Only strings in JSON objects and arrays can be replaced.
            }
        }
        return modified;
    }

    private static boolean resolveJsonStringPlaceholder(JsonPrimitive primitive, UnaryOperator<String> envLookup,
                                                        Consumer<JsonPrimitive> replace) {
        String original = primitive.getAsString();
        String resolved = replaceEnvTokens(original, envLookup, false);
        if (Objects.equals(original, resolved)) {
            return false;
        }
        replace.accept(new JsonPrimitive(resolved));
        return true;
    }

    private static String resolveFormPlaceholders(String payload, UnaryOperator<String> envLookup) {
        String[] pairs = payload.split("&", -1);
        List<String> updated = new ArrayList<>(pairs.length);
        boolean modified = false;
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String rawVal = pair.substring(eq + 1);
                String resolvedVal = replaceEnvTokens(rawVal, envLookup, true);
                if (!Objects.equals(rawVal, resolvedVal)) {
                    updated.add(pair.substring(0, eq) + "=" + resolvedVal);
                    modified = true;
                    continue;
                }
            }
            updated.add(pair);
        }
        return modified ? String.join("&", updated) : payload;
    }

    private static String replaceEnvTokens(String text, UnaryOperator<String> envLookup, boolean urlEncodeValue) {
        Matcher matcher = ENV_VAR_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        do {
            String resolved = envLookup.apply(matcher.group(1));
            String replacement = resolved == null
                    ? matcher.group()
                    : (urlEncodeValue ? URLEncoder.encode(resolved, StandardCharsets.UTF_8) : resolved);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        matcher.appendTail(result);
        return result.toString();
    }

    public static String overrideMaskedPayloadField(String payload, String fieldKey, String resolvedValue) {
        if (StringUtils.isBlank(payload)) {
            return payload;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return overrideJsonFieldOrPlaceholder(payload, fieldKey, resolvedValue);
        }
        if (looksLikeFormUrlEncoded(trimmed)) {
            return overrideFormFieldOrPlaceholder(payload, fieldKey, resolvedValue);
        }
        return payload;
    }

    private static String overrideJsonFieldOrPlaceholder(String payload, String fieldKey, String resolvedValue) {
        try {
            JsonElement parsed = JsonParser.parseString(payload);
            Pattern tokenPattern = exactPlaceholderPattern(fieldKey);
            if (overrideJsonElement(parsed, "", fieldKey, tokenPattern, resolvedValue)) {
                return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(parsed);
            }
        } catch (JsonSyntaxException _) {
            // Leave malformed JSON unchanged.
        }
        return payload;
    }

    private static boolean overrideJsonElement(JsonElement element, String currentPath, String fieldKey,
                                               Pattern tokenPattern, String resolvedValue) {
        boolean modified = false;
        switch (element) {
            case JsonObject obj -> {
                for (String key : new ArrayList<>(obj.keySet())) {
                    String childPath = currentPath.isEmpty() ? key : currentPath + "#" + key;
                    JsonElement child = obj.get(key);
                    switch (child) {
                        case JsonPrimitive primitive when primitive.isString() ->
                                modified |= overrideJsonString(primitive, childPath, fieldKey, tokenPattern, resolvedValue,
                                        replacement -> obj.add(key, replacement));
                        case null, default -> modified |= overrideJsonElement(child, childPath, fieldKey, tokenPattern, resolvedValue);
                    }
                }
            }
            case JsonArray arr -> {
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement item = arr.get(i);
                    int index = i;
                    switch (item) {
                        case JsonPrimitive primitive when primitive.isString() ->
                                modified |= overrideJsonString(primitive, currentPath, fieldKey, tokenPattern, resolvedValue,
                                        replacement -> arr.set(index, replacement));
                        case null, default -> modified |= overrideJsonElement(item, currentPath, fieldKey, tokenPattern, resolvedValue);
                    }
                }
            }
            case null, default -> {
                // Only strings in JSON objects and arrays can be replaced.
            }
        }
        return modified;
    }

    private static boolean overrideJsonString(JsonPrimitive primitive, String path, String fieldKey, Pattern tokenPattern,
                                              String resolvedValue, Consumer<JsonPrimitive> replace) {
        Optional<String> updated = overridePrimitiveString(path, primitive.getAsString(), fieldKey, tokenPattern, resolvedValue);
        if (updated.isEmpty()) {
            return false;
        }
        replace.accept(new JsonPrimitive(updated.get()));
        return true;
    }

    private static Optional<String> overridePrimitiveString(String fieldPath, String currentValue, String fieldKey,
                                                            Pattern tokenPattern, String resolvedValue) {
        if (matchesFieldMask(normalizeFieldPath(fieldPath), fieldKey)) {
            return Optional.of(resolvedValue);
        }
        if (tokenPattern != null && tokenPattern.matcher(currentValue).find()) {
            return Optional.of(tokenPattern.matcher(currentValue).replaceAll(Matcher.quoteReplacement(resolvedValue)));
        }
        return Optional.empty();
    }

    private static String overrideFormFieldOrPlaceholder(String payload, String fieldKey, String resolvedValue) {
        String[] pairs = payload.split("&", -1);
        Pattern tokenPattern = exactPlaceholderPattern(fieldKey);
        String encodedValue = URLEncoder.encode(resolvedValue, StandardCharsets.UTF_8);
        List<String> updated = new ArrayList<>(pairs.length);
        boolean modified = false;
        for (String pair : pairs) {
            String updatedPair = pair;
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String rawKey = pair.substring(0, eq);
                String rawVal = pair.substring(eq + 1);
                String normalizedKey = normalizeFieldPath(decodeFormComponent(rawKey));
                if (matchesFieldMask(normalizedKey, fieldKey)) {
                    updatedPair = rawKey + "=" + encodedValue;
                    modified = true;
                } else if (tokenPattern != null && tokenPattern.matcher(rawVal).find()) {
                    updatedPair = rawKey + "=" + tokenPattern.matcher(rawVal).replaceAll(Matcher.quoteReplacement(encodedValue));
                    modified = true;
                }
            }
            updated.add(updatedPair);
        }
        return modified ? String.join("&", updated) : payload;
    }

    private static Pattern exactPlaceholderPattern(String fieldKey) {
        String varName = fieldEnvironmentVariable(fieldKey);
        return StringUtils.isBlank(varName) ? null : Pattern.compile("\\$\\$" + Pattern.quote(varName) + "(?!\\w)");
    }

    public static boolean looksLikeFormUrlEncoded(String trimmedPayload) {
        return trimmedPayload.contains("=") && !trimmedPayload.startsWith("<");
    }

    private static String decodeFormComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException _) {
            return value;
        }
    }

    private static String leafKeyOf(String normalizedPath) {
        int idx = normalizedPath.lastIndexOf('#');
        return idx >= 0 ? normalizedPath.substring(idx + 1) : normalizedPath;
    }

    private static boolean hasFieldMaskingRules(ReportingArguments reportingArguments) {
        return reportingArguments != null && (!reportingArguments.getExplicitFieldMasks().isEmpty()
                || (!reportingArguments.isShowSecrets() && !reportingArguments.getRuntimeSensitiveFields().isEmpty()));
    }

    public static String maskUrlForDisplay(String url, ReportingArguments reportingArguments) {
        return maskUrl(url, reportingArguments, Set.of(), false);
    }

    public static String maskUrl(String url, ReportingArguments reportingArguments) {
        return maskUrl(url, reportingArguments, Set.of(), true);
    }

    private static String maskUrl(String url, ReportingArguments reportingArguments,
                                  Set<String> mutatedQueryParams, boolean registerReplayVars) {
        if (url == null || !url.startsWith("http")) {
            return url;
        }
        try {
            HttpUrl parsed = HttpUrl.get(url);
            HttpUrl.Builder builder = parsed.newBuilder();
            for (String queryName : new LinkedHashSet<>(parsed.queryParameterNames())) {
                if (!mutatedQueryParams.contains(queryName.toLowerCase(Locale.ROOT))
                        && reportingArguments.shouldMaskQueryParam(queryName)) {
                    String variable = queryEnvironmentVariable(queryName);
                    if (registerReplayVars) {
                        reportingArguments.registerReplayEnvironmentVariable(variable);
                    }
                    int valueCount = parsed.queryParameterValues(queryName).size();
                    builder.removeAllQueryParameters(queryName);
                    for (int i = 0; i < valueCount; i++) {
                        builder.addQueryParameter(queryName, placeholder(variable));
                    }
                }
            }
            return builder.build().toString().replace("%24%24", "$$");
        } catch (IllegalArgumentException _) {
            return url;
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
