package com.endava.cats.fuzzer.contract.util;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.WordUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies path segments before applying resource and operation naming rules.
 * Vocabulary describes grammatical roles, never individual endpoint exceptions.
 */
public final class PathNamingRules {
    private static final Pattern VERSION_SEGMENT = Pattern.compile(
            "^(?:v(?:ersion)?\\d+(?:[._-]\\d+)*(?:(?:[-_])?(?:alpha|beta|rc)\\d*)?|\\d+(?:\\.\\d+)*|\\d{4}-\\d{2}-\\d{2})$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> NAMESPACES = Set.of(
            "api", "rest", "bff", "internal", "external", "public", "private", "admin",
            "webhook", "graphql", "oauth", "oauth2", ".well-known");
    private static final Set<String> OAUTH_NAMESPACES = Set.of("oauth", "oauth2");
    private static final Set<String> OAUTH_STANDARD_ENDPOINTS = Set.of(
            "token", "userinfo", "callback", "jwks", "introspect", "par", "device_authorization", "device-authorization");
    private static final Set<String> CRUD_VERBS = Set.of(
            "retrieve", "get", "fetch", "read", "create", "add", "save", "update",
            "edit", "set", "delete", "remove", "list", "find", "modify");
    private static final Set<String> READ_OPERATIONS = Set.of(
            "validate", "resolve", "search", "preview", "check", "lookup", "inspect", "verify",
            "estimate", "calculate", "compute", "download", "export", "stream");
    private static final Set<String> WRITE_OPERATIONS = Set.of(
            "post", "patch", "send", "approve", "accept", "reject", "cancel", "execute", "submit", "schedule", "configure",
            "refresh", "retry", "resend", "archive", "restore", "rebuild", "upload", "import", "export",
            "register", "purchase", "process", "batch", "generate", "complete", "activate", "deactivate", "enable",
            "disable", "reset", "start", "stop", "suspend", "resume", "void", "merge", "transfer", "sign", "clear",
            "purge", "lock", "unlock", "assign", "unassign", "link", "unlink", "close", "deliver", "publish", "sync",
            "capture", "refund", "confirm", "revoke", "rotate", "initialize", "authenticate", "authorize", "terminate",
            "duplicate", "rename", "move", "share", "unshare", "invite", "reopen", "escalate", "delegate", "claim",
            "release", "enroll", "unenroll", "provision", "deprovision", "clone", "copy", "notify", "subscribe");
    private static final Set<String> NOUN_VERB_HOMOGRAPHS = Set.of(
            "search", "purchase", "process", "schedule", "archive", "export", "import",
            "restore", "refund", "capture", "stream", "download", "upload", "batch", "post", "patch",
            "release", "merge", "share", "invite", "sync", "copy", "lock", "transfer", "claim",
            "stop", "start", "register", "check");
    private static final Map<String, Set<String>> HOMOGRAPH_ALLOWED_HEADS = Map.ofEntries(
            Map.entry("search", Set.of("result", "index", "query", "filter", "history", "log", "term", "suggestion", "setting", "config", "configuration", "criteria", "session", "job", "entry", "rule", "template", "profile")),
            Map.entry("purchase", Set.of("order", "request", "history", "log", "record", "summary", "status")),
            Map.entry("process", Set.of("definition", "instance", "status", "state", "history", "log", "result", "summary", "config", "configuration")),
            Map.entry("schedule", Set.of("slot", "entry", "template", "config", "configuration", "status", "summary", "window", "rule")),
            Map.entry("archive", Set.of("folder", "job", "policy", "rule", "status", "record", "entry", "file", "location")),
            Map.entry("export", Set.of("job", "setting", "file", "result", "status", "history", "request", "config", "configuration", "template", "format", "policy", "rule", "token", "url", "link")),
            Map.entry("import", Set.of("mapping", "batch", "job", "setting", "file", "result", "status", "history", "request", "config", "configuration", "template", "error", "rule", "record")),
            Map.entry("restore", Set.of("point", "job", "request", "status", "result", "history")),
            Map.entry("refund", Set.of("request", "status", "result", "reason", "policy", "rule", "history", "summary")),
            Map.entry("capture", Set.of("device", "status", "result", "request", "session", "config", "setting")),
            Map.entry("stream", Set.of("key", "session", "status", "config", "configuration", "setting", "url", "endpoint")),
            Map.entry("download", Set.of("token", "url", "link", "session", "job", "status", "result", "request", "limit")),
            Map.entry("upload", Set.of("token", "url", "link", "session", "job", "status", "result", "request", "limit", "target")),
            Map.entry("batch", Set.of("job", "request", "result", "status", "item", "record", "entry", "size", "limit", "summary")),
            Map.entry("post", Set.of("comment", "note", "reaction", "tag", "category", "author", "attachment", "history", "status")),
            Map.entry("patch", Set.of("note", "level", "version", "status", "result", "history", "job", "policy")),
            Map.entry("release", Set.of("note", "channel", "version", "candidate", "artifact", "asset", "history", "status", "date", "schedule")),
            Map.entry("merge", Set.of("request", "result", "status", "conflict", "commit", "base", "target", "rule", "policy")),
            Map.entry("share", Set.of("link", "url", "token", "invitation", "request", "permission", "setting", "status")),
            Map.entry("invite", Set.of("code", "link", "url", "token", "request", "status", "template", "limit")),
            Map.entry("sync", Set.of("job", "rule", "status", "state", "history", "log", "config", "configuration", "token", "session", "result", "setting", "schedule", "error")),
            Map.entry("copy", Set.of("job", "result", "status", "request", "history")),
            Map.entry("lock", Set.of("file", "status", "state", "token", "policy")),
            Map.entry("transfer", Set.of("request", "status", "result", "history", "log", "record", "limit", "order", "rule")),
            Map.entry("claim", Set.of("form", "request", "status", "result", "history", "document", "summary")),
            Map.entry("stop", Set.of("word", "list", "time", "point", "location", "order", "reason", "code")),
            Map.entry("start", Set.of("time", "date", "event", "point", "location", "condition")),
            Map.entry("register", Set.of("entry", "record", "summary")),
            Map.entry("check", Set.of("result", "status", "summary", "list", "point", "type", "rule", "history"))
    );
    private static final Set<String> ENVELOPE_ARRAY_PROPERTIES = Set.of(
            "items", "data", "results", "content", "records", "elements", "values", "entries", "list", "resources"
    );
    private static final Set<String> UNCOUNTABLE = LinterVocabulary.UNCOUNTABLE_SEGMENTS;
    private static final Set<String> SINGLETON_HEADS = Stream.concat(
            LinterVocabulary.SINGLETON_SEGMENTS.stream(),
            Stream.of("state", "summary", "audit", "auth", "oauth", "oauth2", "sso", "login", "logout")
    ).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> MODIFIERS = Set.of(
            "resolved", "effective", "default", "available", "active", "inactive", "pending", "current",
            "archived", "enabled", "disabled", "completed", "latest", "recent", "previous", "new");
    private static final Set<String> ONE_PER_PARENT_SINGLETON_HEADS = Set.of(
            "history", "link", "url", "result"
    );
    private static final Set<String> COMPOUND_SINGLETON_HEADS = Set.of(
            "token", "session", "result", "link", "url", "address", "history", "validation"
    );
    private static final Set<String> SINGULAR_S_WORDS = Set.of(
            "axis", "iris", "alias", "atlas", "bias", "canvas", "gas", "lens",
            "bus", "virus", "bonus", "census", "campus", "corpus", "cactus", "octopus",
            "surplus", "prospectus", "nexus", "thesaurus", "focus", "radius", "genus",
            "apparatus", "consensus", "stimulus", "syllabus", "nucleus", "fungus",
            "terminus", "abacus", "chorus", "circus", "minus", "onus", "opus", "sinus",
            "calculus", "impetus"
    );
    private static final Set<String> REPRESENTATIONS = Set.of("csv", "json", "xml", "pdf");
    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "csv", "json", "xml", "pdf", "txt", "html", "yaml", "yml", "doc", "docx", "xls", "xlsx", "zip", "tar", "gz");
    private static final Set<String> IDENTITY_SELECTORS = Set.of("me", "self", "current");
    private static volatile CachedSpecIndex lastSpecIndex;

    private record CachedSpecIndex(WeakReference<Set<String>> keysRef, SpecPathIndex index) {
    }

    private record SpecPathIndex(Set<String> normalizedPaths,
                                 Set<String> itemBackedSegments,
                                 Set<String> parentsWithItemChild,
                                 Map<String, String> shallowestPathByItemPrefix) {
        static final SpecPathIndex EMPTY = new SpecPathIndex(Set.of(), Set.of(), Set.of(), Map.of());
    }

    private PathNamingRules() {
    }

    public enum SegmentRole {
        NAMESPACE, COLLECTION, IDENTIFIER, SINGLETON, OPERATION
    }

    public record Segment(String value, SegmentRole role) {
    }

    public record Analysis(List<Segment> segments, List<String> nounErrors,
                           List<String> pluralErrors, List<String> warnings) {
        public Analysis {
            segments = List.copyOf(segments);
            nounErrors = List.copyOf(nounErrors);
            pluralErrors = List.copyOf(pluralErrors);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Analyzes a path and method using the contract's full path set and PathItem context when available.
     *
     * @param data the fuzzing data containing the path, method, PathItem, and OpenAPI model
     * @return the path naming analysis
     */
    public static Analysis analyze(FuzzingData data) {
        if (data == null) {
            return analyze("", HttpMethod.GET, Collections.emptySet(), false);
        }
        OpenAPI openApi = data.getOpenApi();
        Set<String> contractPaths = openApi != null && openApi.getPaths() != null
                ? openApi.getPaths().keySet()
                : Collections.emptySet();
        PathItem pathItem = data.getPathItem();
        if (pathItem == null && openApi != null && openApi.getPaths() != null && data.getPath() != null) {
            pathItem = openApi.getPaths().get(data.getPath());
        }
        boolean hasSpec = openApi != null && openApi.getPaths() != null && !openApi.getPaths().isEmpty();
        boolean hasCollectionGet = hasCollectionGetResponse(data.getPath(), pathItem, openApi,
                !hasSpec && (data.getMethod() == HttpMethod.GET || data.getMethod() == HttpMethod.HEAD));
        return analyze(data.getPath(), data.getMethod(), contractPaths, hasCollectionGet);
    }

    /**
     * Collections precede identifiers; operations must be terminal. A recognized
     * read operation permits GET/HEAD or POST; mutations require POST. The method
     * alone never exempts a resource name from collection naming requirements.
     */
    public static Analysis analyze(String path, HttpMethod method) {
        return analyze(path, method, Collections.emptySet(), method == HttpMethod.GET || method == HttpMethod.HEAD);
    }

    /**
     * Analyzes a path and HTTP method given the set of all paths in the OpenAPI specification.
     *
     * @param path          the URL path template
     * @param method        the HTTP method
     * @param contractPaths all path templates in the specification
     * @return the path naming analysis
     */
    public static Analysis analyze(String path, HttpMethod method, Set<String> contractPaths) {
        boolean hasSpec = contractPaths != null && !contractPaths.isEmpty();
        boolean hasCollectionGet = !hasSpec && (method == HttpMethod.GET || method == HttpMethod.HEAD);
        return analyze(path, method, contractPaths != null ? contractPaths : Collections.emptySet(), hasCollectionGet);
    }

    private static Analysis analyze(String path, HttpMethod method, Set<String> contractPaths,
                                    boolean hasCollectionGet) {
        String[] elements = expandColonActions(OpenApiUtils.getPathElements(path));
        List<Segment> segments = new ArrayList<>();
        List<String> nounErrors = new ArrayList<>();
        List<String> pluralErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean namespacePrefix = true;
        SpecPathIndex specIndex = getSpecPathIndex(contractPaths);
        boolean hasSiblingItemPath = hasSiblingItemPath(path, specIndex);
        for (int index = 0; index < elements.length; index++) {
            String element = elements[index];
            String lower = element.toLowerCase(Locale.ROOT);
            if (namespacePrefix && (NAMESPACES.contains(lower) || isVersionSegment(lower))
                    && !(index + 1 < elements.length && isPathVariableSegment(elements[index + 1]))) {
                segments.add(new Segment(element, SegmentRole.NAMESPACE));
                continue;
            }
            namespacePrefix = false;
            boolean hasPriorResource = segments.stream().anyMatch(s -> s.role() != SegmentRole.NAMESPACE);
            boolean underWellKnown = index > 0 && ".well-known".equalsIgnoreCase(elements[index - 1]);
            boolean underOauth = index > 0 && OAUTH_NAMESPACES.contains(elements[index - 1].toLowerCase(Locale.ROOT));
            boolean isSpecDocument = !hasPriorResource && index == elements.length - 1
                    && (lower.startsWith("openapi.") || lower.startsWith("swagger."))
                    && hasFormatSuffixOnSegment(lower);
            if (underWellKnown || isSpecDocument || (underOauth && OAUTH_STANDARD_ENDPOINTS.contains(lower))) {
                segments.add(new Segment(element, SegmentRole.SINGLETON));
                continue;
            }
            if (isPathVariableSegment(element) || isIdentitySelector(elements, index)) {
                segments.add(new Segment(element, SegmentRole.IDENTIFIER));
                continue;
            }
            if (hasFormatSuffixOnSegment(element)) {
                segments.add(new Segment(element, SegmentRole.SINGLETON));
                nounErrors.add(element + ": use Accept header content negotiation instead of a file extension on a resource segment");
                continue;
            }
            List<String> words = WordUtils.splitWords(element).stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
            if (words.isEmpty()) {
                continue;
            }
            String head = words.getLast();
            boolean hasSeparator = element.contains("-") || element.contains("_");
            boolean followedByIdentifier = index + 1 < elements.length && isPathVariableSegment(elements[index + 1]);
            boolean itemPathEvidence = followedByIdentifier
                    || (index == elements.length - 1 && (hasSiblingItemPath || specIndex.itemBackedSegments().contains(lower)));
            boolean collectionEvidence = itemPathEvidence || hasCollectionGet;
            boolean singletonLiteral = SINGLETON_HEADS.contains(lower);
            boolean validHomographCompound = isValidHomographCompound(words, hasSeparator);
            boolean homographWithItemPathEvidence = itemPathEvidence
                    && words.size() >= 2
                    && !MODIFIERS.contains(head)
                    && isHomographLeading(words, hasSeparator)
                    && operationWord(List.of(head), hasSeparator) == null;
            boolean unverifiedHomographCollection = validHomographCompound && isPlural(head) && !collectionEvidence;
            String operation = singletonLiteral
                    || homographWithItemPathEvidence
                    || unverifiedHomographCollection
                    || isNounCompound(words, hasSeparator, collectionEvidence, followedByIdentifier)
                    ? null
                    : operationWord(words, hasSeparator);
            if (operation != null) {
                segments.add(new Segment(element, SegmentRole.OPERATION));
                boolean isOauthAuthorize = underOauth && "authorize".equals(operation);
                boolean isVerbLeadingMultiWord = words.size() >= 2 && operation.equals(words.getFirst());
                if (CRUD_VERBS.contains(operation)) {
                    nounErrors.add(element + ": use the HTTP method instead of a CRUD operation in the path");
                } else if (isVerbLeadingMultiWord) {
                    nounErrors.add(element + ": use a noun form or resource collection name instead of a verb-object phrase");
                } else if (index != elements.length - 1) {
                    nounErrors.add(element + ": operations must be terminal; put operation arguments in query parameters or the request body");
                } else if (!hasPriorResource && !underOauth) {
                    nounErrors.add(element + ": place custom operations under a resource or service namespace");
                } else if (method != HttpMethod.POST
                        && !((READ_OPERATIONS.contains(operation) || isOauthAuthorize)
                        && (method == HttpMethod.GET || method == HttpMethod.HEAD))) {
                    nounErrors.add(element + ": use POST for state-changing operations; GET/HEAD are reserved for read operations");
                }
                continue;
            }
            if (MODIFIERS.contains(head)) {
                segments.add(new Segment(element, SegmentRole.SINGLETON));
                nounErrors.add(element + ": a resource name needs a head noun, for example resolved-configuration");
                continue;
            }
            boolean plural = isPlural(head);
            boolean uncountable = UNCOUNTABLE.contains(head);
            boolean repeatsParentNoun = index > 0 && WordUtils.sameNoun(elements[index - 1], element);
            boolean misplacedNamespace = hasPriorResource && NAMESPACES.contains(lower);
            boolean singletonHead = SINGLETON_HEADS.contains(head)
                    || (index > 0 && isPathVariableSegment(elements[index - 1]) && ONE_PER_PARENT_SINGLETON_HEADS.contains(head));
            boolean singletonUnderInstance = index == elements.length - 1 && !segments.isEmpty()
                    && (segments.getLast().role() == SegmentRole.IDENTIFIER || segments.getLast().role() == SegmentRole.SINGLETON)
                    && (ONE_PER_PARENT_SINGLETON_HEADS.contains(head) || isNounCompound(words, hasSeparator, false, false));
            if (followedByIdentifier || plural) {
                segments.add(new Segment(element, SegmentRole.COLLECTION));
                if (!plural) {
                    pluralErrors.add(element + ": collections selected by an identifier must use a plural noun");
                } else if (unverifiedHomographCollection) {
                    warnings.add(element + ": verb-prefixed collection without item path or list GET; confirm the name");
                } else if (homographWithItemPathEvidence && !validHomographCompound
                        && shouldReportHomographCollectionWarning(index, elements, specIndex)) {
                    warnings.add(element + ": verb-prefixed collection name; confirm or rename");
                }
            } else if (uncountable || singletonLiteral || singletonHead || singletonUnderInstance
                    || (index == elements.length - 1 && hasPriorResource && REPRESENTATIONS.contains(lower))) {
                segments.add(new Segment(element, SegmentRole.SINGLETON));
            } else if (index == elements.length - 1 && hasPriorResource && !repeatsParentNoun && !misplacedNamespace
                    && !REPRESENTATIONS.contains(lower)) {
                segments.add(new Segment(element, SegmentRole.SINGLETON));
                warnings.add(element + ": unknown singular resource or operation; confirm its naming role");
            } else {
                segments.add(new Segment(element, SegmentRole.COLLECTION));
                pluralErrors.add(element + ": use a plural collection name or a recognized singleton resource noun");
            }
        }
        return new Analysis(segments, nounErrors, pluralErrors, warnings);
    }

    private static String[] expandColonActions(String[] rawElements) {
        List<String> expanded = new ArrayList<>(rawElements.length);
        for (String element : rawElements) {
            int colonIdx = element.indexOf(':');
            if (colonIdx > 0 && colonIdx < element.length() - 1) {
                expanded.add(element.substring(0, colonIdx));
                expanded.add(element.substring(colonIdx + 1));
            } else {
                expanded.add(element);
            }
        }
        return expanded.toArray(String[]::new);
    }

    private static boolean hasFormatSuffixOnSegment(String element) {
        int dot = element.lastIndexOf('.');
        if (dot <= 0 || dot == element.length() - 1) {
            return false;
        }
        String ext = element.substring(dot + 1).toLowerCase(Locale.ROOT);
        return FILE_EXTENSIONS.contains(ext);
    }

    private static boolean isVersionSegment(String lower) {
        return VERSION_SEGMENT.matcher(lower).matches();
    }

    private static boolean isPathVariableSegment(String element) {
        return element.startsWith("{") && element.endsWith("}");
    }

    private static boolean isIdentitySelector(String[] elements, int index) {
        if (index == 0 || isPathVariableSegment(elements[index - 1])) {
            return false;
        }
        List<String> parent = WordUtils.splitWords(elements[index - 1]).stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
        if (parent.isEmpty() || !isPlural(parent.getLast())) {
            return false;
        }
        List<String> words = WordUtils.splitWords(elements[index]).stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
        return (words.size() == 1 && IDENTITY_SELECTORS.contains(words.getFirst()))
                || (words.size() >= 2 && "current".equals(words.getFirst())
                && WordUtils.sameNoun(WordUtils.normalizeAlphaNumeric(elements[index - 1]), String.join("", words.subList(1, words.size()))));
    }

    private static SpecPathIndex getSpecPathIndex(Set<String> contractPaths) {
        if (contractPaths == null || contractPaths.isEmpty()) {
            return SpecPathIndex.EMPTY;
        }
        CachedSpecIndex cached = lastSpecIndex;
        if (cached != null && cached.keysRef().get() == contractPaths) {
            return cached.index();
        }
        SpecPathIndex built = buildSpecPathIndex(contractPaths);
        lastSpecIndex = new CachedSpecIndex(new WeakReference<>(contractPaths), built);
        return built;
    }

    private static SpecPathIndex buildSpecPathIndex(Set<String> contractPaths) {
        Set<String> normalizedPaths = new HashSet<>(contractPaths.size());
        Set<String> itemBackedSegments = new HashSet<>();
        Set<String> parentsWithItemChild = new HashSet<>();
        Map<String, String> shallowestPathByItemPrefix = new HashMap<>();

        List<String> sortedPaths = contractPaths.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .toList();

        for (String rawPath : sortedPaths) {
            String[] elems = expandColonActions(OpenApiUtils.getPathElements(normalizePath(rawPath)));
            String canonical = canonicalPath(elems);
            normalizedPaths.add(canonical);
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < elems.length; i++) {
                prefix.append('/').append(elems[i]);
                if (i + 1 < elems.length && isPathVariableSegment(elems[i + 1])) {
                    String segLower = elems[i].toLowerCase(Locale.ROOT);
                    itemBackedSegments.add(segLower);
                    String collectionPrefix = prefix.toString();
                    parentsWithItemChild.add(collectionPrefix);
                    String itemPrefix = collectionPrefix + "/{}";
                    shallowestPathByItemPrefix.putIfAbsent(itemPrefix, canonical);
                }
            }
        }
        return new SpecPathIndex(normalizedPaths, itemBackedSegments, parentsWithItemChild, shallowestPathByItemPrefix);
    }

    private static String canonicalPath(String[] elements) {
        return elements.length == 0 ? "/" : "/" + String.join("/", elements);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private static boolean shouldReportHomographCollectionWarning(int index, String[] elements, SpecPathIndex specIndex) {
        if (index == elements.length - 1) {
            return true;
        }
        if (index + 1 < elements.length && isPathVariableSegment(elements[index + 1])) {
            if (specIndex.normalizedPaths().isEmpty()) {
                return true;
            }
            StringBuilder collectionPrefixBuilder = new StringBuilder();
            for (int i = 0; i <= index; i++) {
                collectionPrefixBuilder.append('/').append(elements[i]);
            }
            String collectionPrefix = collectionPrefixBuilder.toString();
            if (specIndex.normalizedPaths().contains(collectionPrefix)) {
                return false;
            }
            String itemPrefix = collectionPrefix + "/{}";
            String shallowest = specIndex.shallowestPathByItemPrefix().get(itemPrefix);
            return shallowest == null || shallowest.equals(canonicalPath(elements));
        }
        return false;
    }

    private static boolean hasSiblingItemPath(String path, SpecPathIndex specIndex) {
        if (path == null || specIndex.parentsWithItemChild().isEmpty()) {
            return false;
        }
        String[] elems = expandColonActions(OpenApiUtils.getPathElements(normalizePath(path)));
        if (elems.length == 0) {
            return false;
        }
        return specIndex.parentsWithItemChild().contains("/" + String.join("/", elems));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasCollectionGetResponse(String path, PathItem pathItem, OpenAPI openApi, boolean noSpecFallback) {
        if (pathItem == null) {
            return noSpecFallback;
        }
        Operation getOp = pathItem.getGet();
        if (getOp == null) {
            return false;
        }
        if (getOp.getResponses() == null || getOp.getResponses().isEmpty()) {
            return noSpecFallback;
        }
        Map<String, Schema> schemas = OpenApiUtils.getComponentSchemas(openApi);
        String lastSegment = path != null
                ? path.substring(path.lastIndexOf('/') + 1).replaceAll("[-_]", "").toLowerCase(Locale.ROOT)
                : "";
        boolean anySchema = false;
        for (Map.Entry<String, ApiResponse> entry : getOp.getResponses().entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith("2") || entry.getValue() == null) {
                continue;
            }
            ApiResponse resolvedResp = OpenApiUtils.resolveApiResponse(entry.getValue(), openApi);
            if (resolvedResp == null || resolvedResp.getContent() == null) {
                continue;
            }
            for (MediaType media : resolvedResp.getContent().values()) {
                if (media != null && media.getSchema() != null) {
                    anySchema = true;
                    Schema<?> resolved = CatsModelUtils.resolveSchema(media.getSchema(), schemas);
                    if (isArrayOrEnvelopeSchema(resolved, schemas, lastSegment, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                        return true;
                    }
                }
            }
        }
        return !anySchema && noSpecFallback;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isArrayOrEnvelopeSchema(Schema<?> schema, Map<String, Schema> schemas,
                                                   String lastSegment, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return false;
        }
        if (CatsModelUtils.isArraySchema(schema)) {
            return true;
        }
        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : ((Map<String, Schema>) schema.getProperties()).entrySet()) {
                String propName = prop.getKey().replaceAll("[-_]", "").toLowerCase(Locale.ROOT);
                Schema<?> propSchema = CatsModelUtils.resolveSchema(prop.getValue(), schemas);
                if (CatsModelUtils.isArraySchema(propSchema)
                        && (ENVELOPE_ARRAY_PROPERTIES.contains(propName) || (!lastSegment.isEmpty() && propName.equals(lastSegment)))) {
                    return true;
                }
            }
        }
        for (Schema<?> composed : CatsModelUtils.getComposedSchemas(schema)) {
            if (isArrayOrEnvelopeSchema(CatsModelUtils.resolveSchema(composed, schemas), schemas, lastSegment, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHomographLeading(List<String> words, boolean hasSeparator) {
        if (words.isEmpty()) {
            return false;
        }
        String firstOp = operationWord(List.of(words.getFirst()), hasSeparator);
        return firstOp != null && NOUN_VERB_HOMOGRAPHS.contains(firstOp);
    }

    private static boolean isValidHomographCompound(List<String> words, boolean hasSeparator) {
        if (words.size() < 2) {
            return false;
        }
        String first = words.getFirst();
        String head = words.getLast();
        if ("add".equals(first) && ("on".equals(head) || "ons".equals(head))) {
            return true;
        }
        String firstOp = operationWord(List.of(first), hasSeparator);
        if (firstOp == null || !NOUN_VERB_HOMOGRAPHS.contains(firstOp)) {
            return false;
        }
        Set<String> allowedHeads = HOMOGRAPH_ALLOWED_HEADS.getOrDefault(firstOp, Collections.emptySet());
        return WordUtils.nounForms(head).stream().anyMatch(allowedHeads::contains);
    }

    private static boolean isNounCompound(List<String> words, boolean hasSeparator,
                                          boolean collectionEvidence, boolean followedByIdentifier) {
        if (words.size() < 2) {
            return false;
        }
        String first = words.getFirst();
        String head = words.getLast();
        if (MODIFIERS.contains(head)) {
            return false;
        }
        if ("add".equals(first) && ("on".equals(head) || "ons".equals(head))) {
            return true;
        }
        String firstOp = operationWord(List.of(first), hasSeparator);
        if (firstOp == null) {
            if (followedByIdentifier || (isPlural(head) && operationWord(List.of(head), hasSeparator) == null)) {
                return true;
            }
            return WordUtils.nounForms(head).stream().anyMatch(word ->
                    COMPOUND_SINGLETON_HEADS.contains(word) || SINGLETON_HEADS.contains(word) || UNCOUNTABLE.contains(word));
        }
        if (!isValidHomographCompound(words, hasSeparator)) {
            return false;
        }
        if (followedByIdentifier) {
            return true;
        }
        if (isPlural(head) && operationWord(List.of(head), hasSeparator) == null) {
            return collectionEvidence;
        }
        return WordUtils.nounForms(head).stream().anyMatch(word ->
                COMPOUND_SINGLETON_HEADS.contains(word) || SINGLETON_HEADS.contains(word));
    }

    private static String operationWord(List<String> words, boolean hasSeparator) {
        String first = words.getFirst();
        String last = words.getLast();
        if (!hasSeparator && words.size() >= 2 && CRUD_VERBS.contains(last)) {
            return last;
        }
        if (CRUD_VERBS.contains(first) || READ_OPERATIONS.contains(first) || WRITE_OPERATIONS.contains(first)
                || (words.size() == 1 && isMorphologicalVerb(first))) {
            return first;
        }
        if (READ_OPERATIONS.contains(last) || WRITE_OPERATIONS.contains(last)
                || (words.size() == 1 && isMorphologicalVerb(last))) {
            return last;
        }
        return null;
    }

    private static boolean isMorphologicalVerb(String word) {
        return word.length() > 5 && (word.endsWith("ize") || word.endsWith("ify"));
    }

    private static boolean isPlural(String head) {
        if (SINGLETON_HEADS.contains(head) || UNCOUNTABLE.contains(head) || SINGULAR_S_WORDS.contains(head)) {
            return false;
        }
        if (head.endsWith("ss") || head.endsWith("sis")) {
            return false;
        }
        return WordUtils.isPlural(head);
    }

    /**
     * Checks whether the terminal segment of the given path represents a non-CRUD operation or singleton resource.
     *
     * @param path the OpenAPI path
     * @return true if the path ends with an operation, modifier, or singleton segment
     */
    public static boolean isOperationOrSingletonSegment(String path) {
        return isOperationOrSingletonSegment(path, null);
    }

    /**
     * Checks whether the terminal segment of the given path represents a non-CRUD operation or singleton resource
     * within the given OpenAPI specification context.
     *
     * @param path    the OpenAPI path
     * @param openApi the OpenAPI specification (may be null)
     * @return true if the path ends with an operation, modifier, or singleton segment
     */
    public static boolean isOperationOrSingletonSegment(String path, OpenAPI openApi) {
        String[] elements = OpenApiUtils.getPathElements(path);
        if (elements.length == 0) {
            return false;
        }
        String last = elements[elements.length - 1];
        if (OpenApiUtils.isAPathVariable(last)) {
            return false;
        }
        String lower = last.toLowerCase(Locale.ROOT);
        if (UNCOUNTABLE.contains(lower)) {
            return false;
        }
        if (openApi != null && openApi.getPaths() != null
                && hasSiblingItemPath(path, getSpecPathIndex(openApi.getPaths().keySet()))) {
            return false;
        }
        Analysis analysis;
        if (openApi != null && openApi.getPaths() != null) {
            PathItem item = openApi.getPaths().get(path);
            HttpMethod primaryMethod = item != null && item.getGet() != null ? HttpMethod.GET : HttpMethod.POST;
            analysis = analyze(FuzzingData.builder().openApi(openApi).path(path).pathItem(item).method(primaryMethod).build());
        } else {
            analysis = analyze(path, HttpMethod.GET);
        }
        if (analysis.segments().isEmpty()) {
            return false;
        }
        SegmentRole lastRole = analysis.segments().getLast().role();
        return lastRole == SegmentRole.OPERATION || lastRole == SegmentRole.SINGLETON;
    }
}
