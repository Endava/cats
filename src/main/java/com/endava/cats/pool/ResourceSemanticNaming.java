package com.endava.cats.pool;

import com.endava.cats.model.RequestTarget;
import com.google.common.base.Splitter;
import com.google.gson.JsonPrimitive;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles semantic naming, path morphology, singularization, alias generation, and value-shape classification
 * for runtime resource correlation.
 */
final class ResourceSemanticNaming {
    static final Set<String> SIMPLE_IDENTIFIERS = Set.of("id", "uuid", "guid");
    static final Set<String> SIMPLE_PLURAL_IDENTIFIERS = Set.of("ids", "uuids", "guids");
    static final Set<String> SELF_IDENTITY_SEGMENTS = Set.of("me", "self", "current", "whoami");

    private static final Pattern SEPARATED_ID = Pattern.compile("(?i).*(?:_|-)(?:id|uuid|guid|token|username|address)(?:s|es)?$");
    private static final Pattern CAMEL_IDENTIFIER = Pattern.compile(
            ".*(?:Id|ID|Uuid|UUID|Guid|GUID|Token|TOKEN|Username|USERNAME|Address|ADDRESS)(?:s|S|es|ES)?$");
    private static final Pattern WORD_BOUNDARY_SPLITTER = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\-]+");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");
    private static final Splitter SEMANTIC_PATH_SPLITTER = Splitter.on(Pattern.compile("[.#]")).omitEmptyStrings();
    private static final Map<String, String> IRREGULAR_SINGULARS = Map.of(
            "children", "child",
            "men", "man",
            "people", "person",
            "statuses", "status",
            "women", "woman");

    private ResourceSemanticNaming() {
        // utility class
    }

    static String normalize(String value) {
        return NON_ALPHANUMERIC.matcher(Optional.ofNullable(value).orElse(""))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }

    static boolean isIdentifierName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SIMPLE_IDENTIFIERS.contains(lower) || SIMPLE_PLURAL_IDENTIFIERS.contains(lower) ||
                "username".equals(lower) || "token".equals(lower) ||
                SEPARATED_ID.matcher(name).matches() ||
                CAMEL_IDENTIFIER.matcher(name).matches();
    }

    static boolean isMetadataKey(String normalizedKey) {
        return normalizedKey.contains("time") || normalizedKey.contains("date") ||
                normalizedKey.contains("status") || normalizedKey.contains("message") ||
                normalizedKey.contains("error") || normalizedKey.equals("code") ||
                normalizedKey.equals("version");
    }

    static boolean isTopLevelResponseKey(String key) {
        String stripped = key.startsWith("$[].") ? key.substring(4) : (key.startsWith("$.") ? key.substring(2) : key);
        return !stripped.contains(".") && !stripped.contains("[]");
    }

    static String resourceName(String path) {
        List<String> segments = pathSegments(path);
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isTemplateSegment(segments.get(i))) {
                return normalize(segments.get(i));
            }
        }
        return "resource";
    }

    static String singular(String resource) {
        String irregular = IRREGULAR_SINGULARS.get(resource);
        if (irregular != null) {
            return irregular;
        }
        if (resource.endsWith("ais") && resource.length() > 3) {
            return resource.substring(0, resource.length() - 3) + "al";
        }
        if (resource.endsWith("ies") && resource.length() > 3) {
            return resource.substring(0, resource.length() - 3) + "y";
        }
        if (resource.endsWith("sses") || resource.endsWith("xes") || resource.endsWith("zes") || resource.endsWith("ches") || resource.endsWith("shes")) {
            return resource.substring(0, resource.length() - 2);
        }
        if (resource.endsWith("s") && !resource.endsWith("ss") && resource.length() > 1) {
            return resource.substring(0, resource.length() - 1);
        }
        return resource;
    }

    static List<String> pathSegments(String path) {
        if (path == null) {
            return List.of();
        }
        return Stream.of(path.split("/")).filter(StringUtils::isNotBlank).toList();
    }

    static boolean isTemplateSegment(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    static boolean isCollectionPath(String path) {
        List<String> segments = pathSegments(path);
        return !segments.isEmpty() && !isTemplateSegment(segments.getLast());
    }

    static boolean sameParentPath(List<String> sourceSegments, List<String> targetSegments) {
        if (sourceSegments.size() != targetSegments.size() || sourceSegments.size() < 2) {
            return false;
        }
        for (int i = 0; i < sourceSegments.size() - 1; i++) {
            if (!sourceSegments.get(i).equalsIgnoreCase(targetSegments.get(i))) {
                return false;
            }
        }
        return !sourceSegments.getLast().equalsIgnoreCase(targetSegments.getLast());
    }

    static String lastPathSegment(String location) {
        try {
            List<String> segments = HttpUrl.get(location).pathSegments().stream()
                    .filter(StringUtils::isNotBlank).toList();
            return segments.isEmpty() ? "" : segments.getLast();
        } catch (IllegalArgumentException _) {
            int queryIndex = location.indexOf('?');
            int fragmentIndex = location.indexOf('#');
            int suffixIndex = queryIndex < 0 ? fragmentIndex :
                    fragmentIndex < 0 ? queryIndex : Math.min(queryIndex, fragmentIndex);
            String path = suffixIndex < 0 ? location : location.substring(0, suffixIndex);
            path = StringUtils.stripEnd(path, "/");
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    static Set<String> semanticAliases(String path, String name) {
        Set<String> aliases = new LinkedHashSet<>();
        addWithSingularIdForms(normalize(name), aliases);
        addWordBoundarySuffixAliases(name, aliases);
        List<String> segments = SEMANTIC_PATH_SPLITTER.splitToList(path.replace("$", ""));
        StringBuilder suffix = new StringBuilder();
        for (int i = segments.size() - 1; i >= 0; i--) {
            suffix.insert(0, segments.get(i));
            addWithSingularIdForms(normalize(suffix.toString()), aliases);
        }
        return Set.copyOf(aliases);
    }

    private static void addWordBoundarySuffixAliases(String rawName, Set<String> aliases) {
        if (StringUtils.isBlank(rawName)) {
            return;
        }
        List<String> words = Splitter.on(WORD_BOUNDARY_SPLITTER).omitEmptyStrings().splitToList(rawName.trim());
        if (words.size() >= 3) {
            String lastWord = normalize(words.getLast());
            if (SIMPLE_IDENTIFIERS.contains(lastWord) || SIMPLE_PLURAL_IDENTIFIERS.contains(lastWord)) {
                String twoWordSuffix = normalize(words.get(words.size() - 2)) + lastWord;
                addWithSingularIdForms(twoWordSuffix, aliases);
            }
        }
        if (words.size() >= 2) {
            String lastWord = normalize(words.getLast());
            String fullNormalized = normalize(rawName);
            if ("address".equals(lastWord) && !fullNormalized.equals("contractaddress") && !fullNormalized.contains("email")) {
                aliases.add("address");
            } else if ("name".equals(lastWord)) {
                aliases.add("name");
            }
        }
    }

    private static void addWithSingularIdForms(String normalizedAlias, Set<String> aliases) {
        if (normalizedAlias.isEmpty()) {
            return;
        }
        aliases.add(normalizedAlias);
        for (String pluralSuffix : SIMPLE_PLURAL_IDENTIFIERS) {
            if (normalizedAlias.endsWith(pluralSuffix)) {
                String singularSuffix = pluralSuffix.substring(0, pluralSuffix.length() - 1);
                String prefix = normalizedAlias.substring(0, normalizedAlias.length() - pluralSuffix.length());
                if (prefix.isEmpty()) {
                    aliases.add(singularSuffix);
                } else {
                    aliases.add(prefix + singularSuffix);
                    aliases.add(singular(prefix) + singularSuffix);
                }
                return;
            }
        }
        for (String singularSuffix : SIMPLE_IDENTIFIERS) {
            if (normalizedAlias.endsWith(singularSuffix) && normalizedAlias.length() > singularSuffix.length()) {
                String prefix = normalizedAlias.substring(0, normalizedAlias.length() - singularSuffix.length());
                aliases.add(singular(prefix) + singularSuffix);
                return;
            }
        }
    }

    static Set<String> resourceIdentifierAliases(String path) {
        List<String> segments = pathSegments(path);
        int lastResourceIndex = -1;
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isTemplateSegment(segments.get(i))) {
                lastResourceIndex = i;
                break;
            }
        }
        if (lastResourceIndex < 0) {
            return Set.of();
        }
        int startIndex = lastResourceIndex;
        while (startIndex > 0 && !isTemplateSegment(segments.get(startIndex - 1))) {
            startIndex--;
        }
        Set<String> aliases = new LinkedHashSet<>();
        segments.subList(startIndex, lastResourceIndex + 1).stream()
                .map(ResourceSemanticNaming::normalize)
                .filter(StringUtils::isNotBlank)
                .forEach(resource -> {
                    aliases.add(resource + "id");
                    aliases.add(singular(resource) + "id");
                });
        return aliases;
    }

    static Set<String> primaryResourceIdentifierAliases(String path) {
        String resource = resourceName(path);
        String singular = singular(resource);
        Set<String> aliases = new LinkedHashSet<>();
        for (String suffix : SIMPLE_IDENTIFIERS) {
            aliases.add(resource + suffix);
            aliases.add(singular + suffix);
        }
        for (String suffix : SIMPLE_PLURAL_IDENTIFIERS) {
            aliases.add(resource + suffix);
            aliases.add(singular + suffix);
        }
        return aliases;
    }

    static Set<String> precedingPathSegmentAliases(String path, String paramName) {
        List<String> segments = pathSegments(path);
        String template = "{" + paramName + "}";
        for (int i = 1; i < segments.size(); i++) {
            if (segments.get(i).equalsIgnoreCase(template) && !isTemplateSegment(segments.get(i - 1))) {
                String resource = normalize(segments.get(i - 1));
                String singular = singular(resource);
                Set<String> aliases = new LinkedHashSet<>();
                for (String suffix : SIMPLE_IDENTIFIERS) {
                    aliases.add(resource + suffix);
                    aliases.add(singular + suffix);
                }
                return aliases;
            }
        }
        return Set.of();
    }

    static Set<String> targetAliases(String targetPath, TargetField target) {
        Set<String> aliases = new LinkedHashSet<>(semanticAliases(target.path(), target.name()));
        if (aliases.stream().anyMatch(SIMPLE_IDENTIFIERS::contains) &&
                (target.location() == RequestTarget.Location.PATH || !target.path().contains("#"))) {
            aliases.addAll(primaryResourceIdentifierAliases(targetPath));
        }
        if (target.location() == RequestTarget.Location.PATH) {
            aliases.addAll(precedingPathSegmentAliases(targetPath, target.name()));
        }
        return Set.copyOf(aliases);
    }

    static String bindingKey(String targetPath, TargetField target) {
        List<String> segments = SEMANTIC_PATH_SPLITTER.splitToList(target.path());
        if (segments.size() >= 2) {
            return singular(normalize(segments.get(segments.size() - 2)));
        }
        String key = normalize(target.name());
        for (String suffix : SIMPLE_PLURAL_IDENTIFIERS) {
            if (key.endsWith(suffix)) {
                key = key.substring(0, key.length() - suffix.length());
                break;
            }
        }
        for (String suffix : SIMPLE_IDENTIFIERS) {
            if (key.endsWith(suffix)) {
                key = key.substring(0, key.length() - suffix.length());
                break;
            }
        }
        for (String qualifier : List.of("external", "internal", "public", "reference", "ref")) {
            if (key.endsWith(qualifier)) {
                key = key.substring(0, key.length() - qualifier.length());
                break;
            }
        }
        return key.isEmpty() ? singular(resourceName(targetPath)) : key;
    }

    static Set<String> deletedAliases(String path, String deletedKey, StoredValue deletedValue) {
        Set<String> aliases = new LinkedHashSet<>(deletedValue.aliases());
        String normalizedKey = normalize(deletedKey);
        aliases.add(normalizedKey);
        if (SIMPLE_IDENTIFIERS.contains(normalizedKey)) {
            Set<String> qualified = new LinkedHashSet<>(primaryResourceIdentifierAliases(path));
            qualified.addAll(precedingPathSegmentAliases(path, deletedKey));
            if (!qualified.isEmpty()) {
                aliases.removeAll(SIMPLE_IDENTIFIERS);
                aliases.addAll(qualified);
            }
        }
        return aliases;
    }

    static int pathAffinity(String sourcePath, String targetPath) {
        List<String> source = pathSegments(sourcePath);
        List<String> target = pathSegments(targetPath);
        int common = 0;
        for (int i = 0; i < Math.min(source.size(), target.size()); i++) {
            String sourceSegment = source.get(i);
            String targetSegment = target.get(i);
            if (isTemplateSegment(sourceSegment) || isTemplateSegment(targetSegment) || sourceSegment.equalsIgnoreCase(targetSegment)) {
                common++;
            } else {
                break;
            }
        }
        return common;
    }

    static int matchSpecificity(StoredValue value, Set<String> targetAliases) {
        return value.aliases().stream().filter(targetAliases::contains)
                .mapToInt(String::length).max().orElse(0);
    }

    static int semanticConfidence(ResolvedValue candidate, String targetPath, Set<String> targetAliases) {
        boolean qualifiedAlias = candidate.storedValue().aliases().stream()
                .filter(alias -> !SIMPLE_IDENTIFIERS.contains(alias))
                .anyMatch(targetAliases::contains);
        if (qualifiedAlias) {
            return 3;
        }
        Set<String> resourceAliases = primaryResourceIdentifierAliases(targetPath);
        return candidate.storedValue().aliases().stream().anyMatch(resourceAliases::contains) ? 2 : 0;
    }

    static int compatibilityQuality(Object candidateValue, TargetField target) {
        ValueShape candidateShape = valueShape(candidateValue);
        if (target.shape() == candidateShape ||
                (target.shape() == ValueShape.STRING && candidateShape.isString())) {
            return 3;
        }
        if ((target.shape().isNumber() && candidateShape.isNumber()) ||
                (target.shape().isNumericString() && candidateShape.isNumericString())) {
            return 2;
        }
        if (target.shape().isString() && candidateShape.isString()) {
            return 1;
        }
        boolean parameter = target.location() == RequestTarget.Location.PATH ||
                target.location() == RequestTarget.Location.QUERY;
        return parameter && target.shape().isNumeric() && candidateShape.isNumeric() ? 1 : 0;
    }

    static ValueShape valueShape(JsonPrimitive value) {
        if (value == null) {
            return ValueShape.STRING;
        }
        if (value.isBoolean()) {
            return ValueShape.BOOLEAN;
        }
        if (value.isNumber()) {
            return numberShape(new BigDecimal(value.getAsString()));
        }
        return stringShape(value.getAsString());
    }

    static ValueShape valueShape(Object value) {
        if (value instanceof Boolean) {
            return ValueShape.BOOLEAN;
        }
        if (value instanceof Number number) {
            return numberShape(new BigDecimal(number.toString()));
        }
        return stringShape(String.valueOf(value));
    }

    static ValueShape stringShape(String value) {
        if (UUID_VALUE.matcher(value).matches()) {
            return ValueShape.UUID_STRING;
        }
        try {
            BigDecimal number = new BigDecimal(value);
            return number.stripTrailingZeros().scale() <= 0 ? ValueShape.INTEGER_STRING : ValueShape.DECIMAL_STRING;
        } catch (NumberFormatException _) {
            return ValueShape.STRING;
        }
    }

    static ValueShape numberShape(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0 ? ValueShape.INTEGER_NUMBER : ValueShape.DECIMAL_NUMBER;
    }

    static Object primitiveValue(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return new BigDecimal(primitive.getAsString());
        }
        return primitive.getAsString();
    }

    static boolean sameValue(Object generated, Object actual) {
        String generatedValue = String.valueOf(generated);
        String actualValue = String.valueOf(actual);
        if (generatedValue.equals(actualValue)) {
            return true;
        }
        try {
            return new BigDecimal(generatedValue).compareTo(new BigDecimal(actualValue)) == 0;
        } catch (NumberFormatException _) {
            return false;
        }
    }
}
