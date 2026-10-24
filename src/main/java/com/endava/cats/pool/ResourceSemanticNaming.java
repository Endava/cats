package com.endava.cats.pool;

import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.WordUtils;
import com.google.common.base.Splitter;
import com.google.gson.JsonPrimitive;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles semantic naming, path morphology, singularization, alias generation, and value-shape classification
 * for runtime resource correlation.
 */
final class ResourceSemanticNaming {
    static final Set<String> SIMPLE_IDENTIFIERS = WordUtils.PRIMARY_IDENTIFIER_NAMES;
    static final Set<String> SELF_IDENTITY_SEGMENTS = Set.of("me", "self", "current", "whoami");

    private static final Set<String> SINGLE_WORD_IDENTIFIERS = Stream.concat(
            SIMPLE_IDENTIFIERS.stream(),
            Stream.of("username", "token", "key")).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> MULTI_WORD_IDENTIFIER_SUFFIXES = Stream.concat(
            SINGLE_WORD_IDENTIFIERS.stream(),
            Stream.of("address")).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> GENERIC_FALLBACK_SUFFIXES = Stream.concat(
            SINGLE_WORD_IDENTIFIERS.stream(),
            Stream.concat(SIMPLE_IDENTIFIERS.stream().map(id -> id + "s"), Stream.of("name")))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> COMPOUND_SUFFIX_GUARD = Stream.concat(
            GENERIC_FALLBACK_SUFFIXES.stream(),
            Stream.of("address", "code", "codes", "type")).collect(Collectors.toUnmodifiableSet());
    static final Set<String> COLLECTION_WRAPPER_SEGMENTS = Set.of(
            "content", "data", "items", "results", "records", "elements", "entries", "list", "rows", "values", "response");
    private static final Set<String> ENVELOPE_METADATA_TOKENS = Set.of(
            "time", "timestamp", "date", "status", "httpstatus", "statuscode", "errorcode",
            "message", "messages", "error", "errors", "code", "version", "path", "success", "issuccess", "ok",
            "kind", "meta", "metadata", "self", "next", "prev");
    private static final Set<String> ITEM_METADATA_WORDS = Set.of(
            "time", "timestamp", "date", "datetime", "status", "httpstatus", "statuscode", "errorcode",
            "message", "error");
    private static final Set<String> PER_REQUEST_TRACE_SUFFIXES = Set.of(
            "correlationid", "requestid", "traceid", "spanid", "idempotencykey", "traceparent", "tracestate");

    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern DATE_PART = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME_PART = Pattern.compile(
            "^\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:?\\d{2})?$");
    private static final Splitter SEMANTIC_PATH_SPLITTER = Splitter.on(Pattern.compile("[.#]")).omitEmptyStrings();

    private ResourceSemanticNaming() {
        // utility class
    }

    static String normalize(String value) {
        return WordUtils.normalizeAlphaNumeric(value);
    }

    static boolean isIdentifierName(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        List<String> words = splitWords(name);
        if (words.isEmpty()) {
            return false;
        }
        Set<String> lastWordForms = WordUtils.nounForms(normalize(words.getLast()));
        if (words.size() == 1) {
            return !Collections.disjoint(lastWordForms, SINGLE_WORD_IDENTIFIERS);
        }
        return !Collections.disjoint(lastWordForms, MULTI_WORD_IDENTIFIER_SUFFIXES);
    }

    static boolean isMetadataKey(String rawOrNormalizedKey) {
        if (StringUtils.isBlank(rawOrNormalizedKey) || isIdentifierLikeKey(rawOrNormalizedKey)) {
            return false;
        }
        String normalizedKey = normalize(rawOrNormalizedKey);
        if (ENVELOPE_METADATA_TOKENS.contains(normalizedKey)) {
            return true;
        }
        return isItemMetadataKey(rawOrNormalizedKey);
    }

    static boolean isItemMetadataKey(String rawKey) {
        if (StringUtils.isBlank(rawKey) || isIdentifierLikeKey(rawKey)) {
            return false;
        }
        if (WordUtils.isAuditTimestampField(rawKey) || ITEM_METADATA_WORDS.contains(normalize(rawKey))) {
            return true;
        }
        for (String word : splitWords(rawKey)) {
            if (!Collections.disjoint(WordUtils.nounForms(normalize(word)), ITEM_METADATA_WORDS)) {
                return true;
            }
        }
        return false;
    }

    static boolean isTemporalValue(Object value) {
        if (!(value instanceof String text) || text.length() < 10 || !DATE_PART.matcher(text.substring(0, 10)).matches()) {
            return false;
        }
        if (text.length() == 10) {
            return true;
        }
        char sep = text.charAt(10);
        return (sep == 'T' || sep == ' ') && TIME_PART.matcher(text.substring(11)).matches();
    }

    private static boolean isIdentifierLikeKey(String key) {
        if (isIdentifierName(key)) {
            return true;
        }
        String norm = normalize(key);
        for (String suffix : MULTI_WORD_IDENTIFIER_SUFFIXES) {
            if (norm.length() > suffix.length() && (norm.endsWith(suffix) || norm.endsWith(suffix + "s")) &&
                    !"address".equals(suffix)) {
                return true;
            }
        }
        return false;
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
        return WordUtils.singularize(resource);
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
        aliases.addAll(WordUtils.nounForms(normalize(name)));
        addWordBoundarySuffixAliases(name, aliases);
        List<String> segments = SEMANTIC_PATH_SPLITTER.splitToList(path.replace("$", ""));
        StringBuilder suffix = new StringBuilder();
        for (int i = segments.size() - 1; i >= 0; i--) {
            String segment = segments.get(i);
            suffix.insert(0, segment);
            addWithSingularIdForms(normalize(suffix.toString()), aliases);
            if (segment.contains("[]")) {
                addArraySegmentAliases(segment, name, aliases);
            }
        }
        return Set.copyOf(aliases);
    }

    private static void addArraySegmentAliases(String segment, String name, Set<String> aliases) {
        String cleanSeg = normalize(segment);
        if (cleanSeg.isEmpty() || COLLECTION_WRAPPER_SEGMENTS.contains(cleanSeg)) {
            return;
        }
        boolean identifier = isIdentifierName(name);
        String normName = normalize(name);
        for (String form : WordUtils.nounForms(cleanSeg)) {
            aliases.add(form + normName);
            if (identifier) {
                aliases.add(form);
            }
        }
    }

    private static List<String> splitWords(String rawName) {
        return WordUtils.splitWords(rawName);
    }

    private static void addWordBoundarySuffixAliases(String rawName, Set<String> aliases) {
        if (StringUtils.isBlank(rawName)) {
            return;
        }
        List<String> words = splitWords(rawName);
        if (words.size() >= 3) {
            String lastWord = normalize(words.getLast());
            if (!Collections.disjoint(WordUtils.nounForms(lastWord), SIMPLE_IDENTIFIERS)) {
                String twoWordSuffix = normalize(words.get(words.size() - 2)) + lastWord;
                addWithSingularIdForms(twoWordSuffix, aliases);
            }
        }
        if (words.size() >= 2) {
            for (String lastWord : WordUtils.nounForms(normalize(words.getLast()))) {
                if (GENERIC_FALLBACK_SUFFIXES.contains(lastWord) && !SIMPLE_IDENTIFIERS.contains(lastWord)) {
                    aliases.add(lastWord);
                }
            }
        }
    }

    private static void addWithSingularIdForms(String normalizedAlias, Set<String> aliases) {
        if (normalizedAlias.isEmpty()) {
            return;
        }
        aliases.add(normalizedAlias);
        for (String singularSuffix : SIMPLE_IDENTIFIERS) {
            String pluralSuffix = singularSuffix + "s";
            if (normalizedAlias.endsWith(pluralSuffix)) {
                String prefix = normalizedAlias.substring(0, normalizedAlias.length() - pluralSuffix.length());
                if (prefix.isEmpty()) {
                    aliases.add(singularSuffix);
                } else {
                    addFormsWithSuffix(prefix, singularSuffix, aliases);
                }
                return;
            }
            if (normalizedAlias.endsWith(singularSuffix) && normalizedAlias.length() > singularSuffix.length()) {
                String prefix = normalizedAlias.substring(0, normalizedAlias.length() - singularSuffix.length());
                addFormsWithSuffix(prefix, singularSuffix, aliases);
                return;
            }
        }
    }

    private static void addFormsWithSuffix(String noun, String suffix, Set<String> aliases) {
        for (String form : WordUtils.nounForms(noun)) {
            aliases.add(form + suffix);
        }
    }

    static String lastRawResourceSegment(String path) {
        List<String> segments = pathSegments(path);
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isTemplateSegment(segments.get(i))) {
                return segments.get(i);
            }
        }
        return "resource";
    }

    static Set<String> resourceWithTailWordForms(String rawSegment) {
        String normalized = normalize(rawSegment);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        Set<String> forms = new LinkedHashSet<>(WordUtils.nounForms(normalized));
        List<String> words = splitWords(rawSegment);
        if (words.size() >= 2) {
            String tailWord = normalize(words.getLast());
            if (!tailWord.isEmpty()) {
                forms.addAll(WordUtils.nounForms(tailWord));
            }
        }
        return forms;
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
        for (String seg : segments.subList(startIndex, lastResourceIndex + 1)) {
            for (String form : resourceWithTailWordForms(seg)) {
                aliases.add(form + "id");
            }
        }
        return aliases;
    }

    static Set<String> primaryResourceIdentifierAliases(String path) {
        String rawResource = lastRawResourceSegment(path);
        Set<String> aliases = new LinkedHashSet<>();
        for (String form : resourceWithTailWordForms(rawResource)) {
            for (String suffix : SIMPLE_IDENTIFIERS) {
                aliases.add(form + suffix);
                aliases.add(form + suffix + "s");
            }
        }
        return aliases;
    }

    static Set<String> precedingPathSegmentAliases(String path, String paramName) {
        List<String> segments = pathSegments(path);
        String template = "{" + paramName + "}";
        if (!segments.isEmpty() && segments.getFirst().equalsIgnoreCase(template)) {
            return SIMPLE_IDENTIFIERS;
        }
        for (int i = 1; i < segments.size(); i++) {
            if (segments.get(i).equalsIgnoreCase(template) && !isTemplateSegment(segments.get(i - 1))) {
                String resource = normalize(segments.get(i - 1));
                Set<String> aliases = new LinkedHashSet<>();
                for (String suffix : SIMPLE_IDENTIFIERS) {
                    addFormsWithSuffix(resource, suffix, aliases);
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
        if (target.location() == RequestTarget.Location.PATH || target.location() == RequestTarget.Location.QUERY
                || (target.location() == RequestTarget.Location.BODY && !target.path().contains("#"))) {
            addResourceQualifiedTargetAliases(targetPath, target.name(), aliases);
            List<String> words = splitWords(target.name());
            if (words.size() == 2 && words.getFirst().length() >= 3 && words.getFirst().chars().noneMatch(Character::isDigit)) {
                String lastWord = normalize(words.getLast());
                if (!lastWord.isEmpty() && !COMPOUND_SUFFIX_GUARD.contains(lastWord) && !ITEM_METADATA_WORDS.contains(lastWord)) {
                    aliases.addAll(WordUtils.nounForms(lastWord));
                }
            }
        }
        if (target.location() == RequestTarget.Location.PATH) {
            aliases.addAll(precedingPathSegmentAliases(targetPath, target.name()));
        }
        return Set.copyOf(aliases);
    }

    private static void addResourceQualifiedTargetAliases(String targetPath, String targetName, Set<String> aliases) {
        String key = normalize(targetName);
        String resource = resourceName(targetPath);
        if (key.isEmpty() || SIMPLE_IDENTIFIERS.contains(key) || isIdentifierName(targetName) || WordUtils.sameNoun(key, resource)) {
            return;
        }
        Set<String> resourcePrefixes = new LinkedHashSet<>(WordUtils.nounForms(resource));
        resourcePrefixes.addAll(headWordFormsOfResourcePath(targetPath));
        for (String resourceForm : resourcePrefixes) {
            for (String keyForm : WordUtils.nounForms(key)) {
                aliases.add(resourceForm + keyForm);
            }
        }
    }

    static Set<String> headWordFormsOfResourcePath(String path) {
        List<String> segments = pathSegments(path);
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isTemplateSegment(segments.get(i))) {
                List<String> words = splitWords(segments.get(i));
                if (words.size() >= 2) {
                    return WordUtils.nounForms(normalize(words.getFirst()));
                }
                break;
            }
        }
        return Set.of();
    }

    static String bindingKey(String targetPath, TargetField target) {
        List<String> segments = SEMANTIC_PATH_SPLITTER.splitToList(target.path());
        if (segments.size() >= 2) {
            return singular(normalize(segments.get(segments.size() - 2)));
        }
        String key = stripIdentifierSuffix(normalize(target.name()));
        key = stripFirstMatchingSuffix(key, List.of("external", "internal", "public", "reference", "ref"));
        return key.isEmpty() ? singular(resourceName(targetPath)) : key;
    }

    private static String stripIdentifierSuffix(String key) {
        for (String suffix : SIMPLE_IDENTIFIERS) {
            String pluralSuffix = suffix + "s";
            if (key.endsWith(pluralSuffix)) {
                return key.substring(0, key.length() - pluralSuffix.length());
            }
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
    }

    private static String stripFirstMatchingSuffix(String key, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
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
        return pathAffinity(pathSegments(sourcePath), pathSegments(targetPath));
    }

    static int pathAffinity(List<String> source, List<String> target) {
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

    static boolean isPerRequestTraceKey(String rawName) {
        if (StringUtils.isBlank(rawName)) {
            return false;
        }
        String normalized = normalize(rawName);
        return PER_REQUEST_TRACE_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    static boolean isCorrelatableHeader(String rawHeaderName) {
        if (StringUtils.isBlank(rawHeaderName) || isPerRequestTraceKey(rawHeaderName)) {
            return false;
        }
        if (isIdentifierName(rawHeaderName)) {
            return true;
        }
        String normalized = normalize(rawHeaderName);
        return Set.of("ifmatch", "ifnonematch", "etag", "location", "contentlocation", "operationlocation").contains(normalized) ||
                normalized.contains("version") || normalized.contains("token") || normalized.contains("key") ||
                normalized.contains("auth") || normalized.contains("session") || normalized.contains("cookie") ||
                normalized.contains("csrf") || normalized.contains("xsrf") || normalized.contains("tenant") ||
                normalized.contains("account") || normalized.contains("user") || normalized.contains("client");
    }

    record TargetMatchContext(Set<String> targetAliases, Set<String> targetSingulars,
                              Set<String> primaryResourceAliases, Set<String> allDirectAliases,
                              List<String> targetPathSegments, Set<String> targetResourceForms,
                              String digitMatchResource, String singleWordTargetPrefix) {
    }

    static TargetMatchContext createTargetMatchContext(String targetPath, TargetField target, Set<String> targetAliases) {
        Set<String> targetSingulars = targetAliases.stream()
                .filter(alias -> !GENERIC_FALLBACK_SUFFIXES.contains(alias))
                .flatMap(alias -> WordUtils.nounForms(alias).stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<String> primaryResourceAliases = targetAliases.stream().anyMatch(SIMPLE_IDENTIFIERS::contains)
                ? primaryResourceIdentifierAliases(targetPath)
                : Set.of();
        Set<String> allDirectAliases = new LinkedHashSet<>(targetAliases);
        allDirectAliases.addAll(targetSingulars);
        allDirectAliases.addAll(primaryResourceAliases);
        List<String> targetPathSegments = pathSegments(targetPath);
        String rawResource = resourceName(targetPath);
        Set<String> targetResourceForms = WordUtils.nounForms(rawResource);
        String resource = singular(rawResource);
        String digitMatchResource = (!resource.isEmpty() && !"resource".equals(resource) &&
                targetAliases.stream().anyMatch(a -> a.startsWith(resource) && a.length() > resource.length()))
                ? resource
                : null;
        String singleWordTargetPrefix = null;
        if (target != null && target.location() != RequestTarget.Location.HEADER &&
                splitWords(target.name()).size() == 1) {
            String normTarget = normalize(target.name());
            if (isPrefixCandidate(normTarget)) {
                singleWordTargetPrefix = singular(normTarget);
            }
        }
        return new TargetMatchContext(targetAliases, targetSingulars, primaryResourceAliases,
                Set.copyOf(allDirectAliases), targetPathSegments, targetResourceForms,
                digitMatchResource, singleWordTargetPrefix);
    }

    /**
     * Computes a graduated name-similarity score between a stored candidate value and a target field:
     * <ul>
     *   <li>{@code 100} — Exact match on a non-generic alias</li>
     *   <li>{@code 90} — Singular/plural match on a non-generic alias</li>
     *   <li>{@code 80} — Resource-qualified identifier match on the target path</li>
     *   <li>{@code 50} — Head-word prefix match of at least 4 characters (e.g. {@code lang} ↔ {@code language})</li>
     *   <li>{@code 30} — Generic suffix match (e.g. {@code id}, {@code token}, {@code username}, {@code address})</li>
     *   <li>{@code 0} — No semantic match</li>
     * </ul>
     */
    static int nameSimilarity(StoredValue value, String targetPath, TargetField target, Set<String> targetAliases) {
        return nameSimilarity(value, createTargetMatchContext(targetPath, target, targetAliases));
    }

    static int nameSimilarity(StoredValue value, TargetMatchContext ctx) {
        Set<String> sourceAliases = value.aliases();
        int exactScore = exactAliasSimilarity(sourceAliases, ctx.targetAliases());
        if (exactScore == 100) {
            return 100;
        }
        if (hasSingularNounFormMatch(sourceAliases, ctx.targetSingulars())) {
            return 90;
        }
        if (hasInsertedMiddleQualifierMatch(sourceAliases, ctx.targetSingulars())) {
            return 85;
        }
        if (hasPrimaryOrDigitResourceMatch(sourceAliases, ctx)) {
            return 80;
        }
        if (ctx.singleWordTargetPrefix() != null && hasHeadWordPrefixMatch(sourceAliases, ctx.singleWordTargetPrefix())) {
            return 50;
        }
        return exactScore;
    }

    private static int exactAliasSimilarity(Set<String> sourceAliases, Set<String> targetAliases) {
        boolean hasGenericExact = false;
        for (String alias : sourceAliases) {
            if (targetAliases.contains(alias)) {
                if (!GENERIC_FALLBACK_SUFFIXES.contains(alias)) {
                    return 100;
                }
                hasGenericExact = true;
            }
        }
        return hasGenericExact ? 30 : 0;
    }

    private static boolean hasSingularNounFormMatch(Set<String> sourceAliases, Set<String> targetSingulars) {
        for (String alias : sourceAliases) {
            if (!GENERIC_FALLBACK_SUFFIXES.contains(alias) &&
                    !Collections.disjoint(WordUtils.nounForms(alias), targetSingulars)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInsertedMiddleQualifierMatch(Set<String> sourceAliases, Set<String> targetSingulars) {
        for (String alias : sourceAliases) {
            if (!GENERIC_FALLBACK_SUFFIXES.contains(alias) &&
                    matchesAnyTargetWithMiddleQualifier(WordUtils.nounForms(alias), targetSingulars)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyTargetWithMiddleQualifier(Set<String> aliasForms, Set<String> targetSingulars) {
        for (String aliasForm : aliasForms) {
            for (String targetForm : targetSingulars) {
                if (WordUtils.matchesWithInsertedMiddleQualifier(aliasForm, targetForm)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasPrimaryOrDigitResourceMatch(Set<String> sourceAliases, TargetMatchContext ctx) {
        return (!ctx.primaryResourceAliases().isEmpty() && sourceAliases.stream().anyMatch(ctx.primaryResourceAliases()::contains)) ||
                hasResourceQualifiedDigitMatch(sourceAliases, ctx.digitMatchResource(), ctx.targetAliases());
    }

    private static boolean hasResourceQualifiedDigitMatch(Set<String> sourceAliases, String resource, Set<String> targetAliases) {
        if (resource == null) {
            return false;
        }
        for (String alias : sourceAliases) {
            if (alias.startsWith(resource) && alias.length() > resource.length() &&
                    Character.isDigit(alias.charAt(resource.length()))) {
                String stripped = resource + alias.substring(resource.length()).replaceFirst("^\\d+", "");
                if (targetAliases.contains(stripped)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasHeadWordPrefixMatch(Set<String> sourceAliases, String singleWordTargetPrefix) {
        for (String src : sourceAliases) {
            if (isPrefixCandidate(src) && isHeadPrefixOf(src, singleWordTargetPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefixCandidate(String alias) {
        if (alias == null || alias.length() < 4 || GENERIC_FALLBACK_SUFFIXES.contains(alias) ||
                ENVELOPE_METADATA_TOKENS.contains(alias)) {
            return false;
        }
        for (String suffix : COMPOUND_SUFFIX_GUARD) {
            if (alias.length() > suffix.length() && alias.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHeadPrefixOf(String first, String second) {
        String shorter = singular(first.length() <= second.length() ? first : second);
        String longer = singular(first.length() <= second.length() ? second : first);
        return shorter.length() >= 4 && longer.length() >= shorter.length() + 2 && longer.startsWith(shorter);
    }

    static int matchSpecificity(StoredValue value, Set<String> targetAliases) {
        return value.aliases().stream().filter(targetAliases::contains)
                .mapToInt(String::length).max().orElse(0);
    }

    static int semanticConfidence(ResolvedValue candidate, int similarity, TargetMatchContext ctx) {
        if (similarity >= 90) {
            return 4;
        }
        if (similarity >= 80) {
            return 3;
        }
        if (similarity >= 50) {
            return 2;
        }
        if (similarity >= 30) {
            boolean nonIdFallback = candidate.storedValue().aliases().stream()
                    .filter(alias -> Collections.disjoint(WordUtils.nounForms(alias), SIMPLE_IDENTIFIERS))
                    .anyMatch(ctx.targetAliases()::contains);
            return nonIdFallback ? 1 : 0;
        }
        return 0;
    }

    static int compatibilityQuality(StoredValue candidate, TargetField target) {
        return compatibilityQuality(candidate.shape(), target);
    }

    static int compatibilityQuality(Object candidateValue, TargetField target) {
        return compatibilityQuality(valueShape(candidateValue), target);
    }

    static int compatibilityQuality(ValueShape candidateShape, TargetField target) {
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
        if (value instanceof BigDecimal decimal) {
            return numberShape(decimal);
        }
        if (value instanceof Number number) {
            return numberShape(new BigDecimal(number.toString()));
        }
        return stringShape(String.valueOf(value));
    }

    static ValueShape stringShape(String value) {
        if (value == null || value.isEmpty()) {
            return ValueShape.STRING;
        }
        if (value.length() == 36 && value.charAt(8) == '-' && UUID_VALUE.matcher(value).matches()) {
            return ValueShape.UUID_STRING;
        }
        if (!looksLikeNumber(value)) {
            return ValueShape.STRING;
        }
        try {
            BigDecimal number = new BigDecimal(value);
            return number.stripTrailingZeros().scale() <= 0 ? ValueShape.INTEGER_STRING : ValueShape.DECIMAL_STRING;
        } catch (NumberFormatException _) {
            return ValueShape.STRING;
        }
    }

    private static boolean looksLikeNumber(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        int start = (value.charAt(0) == '-' || value.charAt(0) == '+') ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        boolean hasDigit = false;
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') {
                hasDigit = true;
            } else if (ch != '.' && ch != 'e' && ch != 'E' && ch != '-' && ch != '+') {
                return false;
            }
        }
        return hasDigit;
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
        if (Objects.equals(generated, actual)) {
            return true;
        }
        String generatedValue = String.valueOf(generated);
        String actualValue = String.valueOf(actual);
        if (generatedValue.equals(actualValue)) {
            return true;
        }
        if (!looksLikeNumber(generatedValue) || !looksLikeNumber(actualValue)) {
            return false;
        }
        try {
            return new BigDecimal(generatedValue).compareTo(new BigDecimal(actualValue)) == 0;
        } catch (NumberFormatException _) {
            return false;
        }
    }
}
