package com.endava.cats.util;

import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Helper class for words operations.
 */
public abstract class WordUtils {
    private static final Map<String, String> IRREGULAR_SINGULARS = Map.ofEntries(
            Map.entry("children", "child"),
            Map.entry("men", "man"),
            Map.entry("women", "woman"),
            Map.entry("people", "person"),
            Map.entry("mice", "mouse"),
            Map.entry("geese", "goose"),
            Map.entry("teeth", "tooth"),
            Map.entry("feet", "foot"),
            Map.entry("oxen", "ox"),
            Map.entry("indices", "index"),
            Map.entry("matrices", "matrix"),
            Map.entry("vertices", "vertex"),
            Map.entry("appendices", "appendix"),
            Map.entry("criteria", "criterion"),
            Map.entry("phenomena", "phenomenon"),
            Map.entry("media", "medium"),
            Map.entry("curricula", "curriculum"),
            Map.entry("alumni", "alumnus"),
            Map.entry("nuclei", "nucleus"),
            Map.entry("stimuli", "stimulus"),
            Map.entry("syllabi", "syllabus"),
            Map.entry("radii", "radius"),
            Map.entry("foci", "focus"),
            Map.entry("quizzes", "quiz"),
            Map.entry("axes", "axis"));
    private static final int MAX_FORMS_CACHE_SIZE = 20_000;
    private static final Map<String, Set<String>> NOUN_FORMS_CACHE = new ConcurrentHashMap<>();
    private static final Splitter WORD_BOUNDARY_SPLITTER = Splitter.on(
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^A-Za-z0-9]+")).omitEmptyStrings();
    public static final Set<String> PRIMARY_IDENTIFIER_NAMES = Set.of("id", "uuid", "guid");
    public static final List<String> IDENTIFIER_SUFFIXES = List.of("id", "uuid", "guid", "token");
    private static final Set<String> AUDIT_TIMESTAMP_PREFIX_WORDS = Set.of(
            "create", "created", "creation", "update", "updated", "modify", "modified", "modification", "deleted", "last");
    private static final Set<String> AUDIT_TIMESTAMP_SUFFIX_WORDS = Set.of(
            "at", "on", "time", "date", "timestamp", "modified", "updated");
    private static final Set<String> AUDIT_TIMESTAMP_NORMALIZED = Set.of(
            "createdat", "updatedat", "modifiedat", "deletedat", "lastmodified", "lastmodifiedat",
            "lastupdated", "lastupdatedat", "updatedon", "modifiedon");
    private static final Set<String> ERROR_KEYWORDS = Set.of(
            "StackTrace", "BadRequest", "InternalServerError", "Unauthorized",
            "Forbidden", "ServiceUnavailable", "SocketTimeout", "PermissionDenied", "InvalidToken",
            "ReadTimeout", "WriteTimeout", "ConnectTimeout", "RequestTimeout", "ResponseTimeout",
            "MethodNotAllowed", "ResourceNotFound", "RateLimitExceeded", "ClientError", "ServerError",
            "AuthenticationFailed", "AuthenticationError", "AuthorizationError", "ConnectionTimeout",
            "SSLHandshakeError", "ConnectionRefused", "MalformedRequest", "MissingParameter",
            "InvalidResponse", "TransactionFailed", "InvalidHeader", "InvalidGrant",
            "APIKeyError", "DatabaseError", "QueryTimeout", "DataLossError", "RequestEntityTooLarge",
            "TooManyRequests", "UnsupportedMediaType", "UnprocessableEntity", "DataIntegrityViolation",

            // JavaScript/Node.js
            "ReferenceError", "SyntaxError", "TypeError", "RangeError", "EvalError", "UncaughtException",
            "UnhandledRejection", "ProcessError", "HeapOverflow", "TimeoutError", "ENOTFOUND",
            "ECONNREFUSED", "EADDRINUSE", "EPIPE", "ETIMEDOUT", "EPERM", "ENETUNREACH", "ECONNRESET",
            "EEXIST", "EISDIR",

            // C#
            "ArgumentNullException", "InvalidOperationException", "TaskCanceledException",
            "FileLoadException", "UnauthorizedAccessException", "KeyNotFoundException", "SecurityException",
            "ArgumentOutOfRangeException", "DirectoryNotFoundException", "OperationCanceledException",
            "StackOverflowException", "FormatException", "NotImplementedException",
            "InvalidCastException", "TimeoutException", "OverflowException", "DivideByZeroException",
            "ObjectDisposedException", "IndexOutOfRangeException",

            // Python
            "ValueError", "KeyError", "AttributeError", "IndexError", "ModuleNotFoundError",
            "ZeroDivisionError", "ImportError", "IOError", "RuntimeError",
            "FileNotFoundError", "StopIteration", "MemoryError", "FloatingPointError",
            "ConnectionError", "AssertionError", "BrokenPipeError", "PermissionError",

            // Go (Golang)
            "panic", "runtime error", "json: cannot unmarshal", "unexpected end of JSON",
            "InvalidArgumentError", "NilPointerDereference", "ChannelClosedError", "DivideByZeroError",
            "fatal error: stack overflow", "index out of range",
            "invalid memory address or nil pointer dereference", "deadlock",

            // Ruby
            "NoMethodError", "ArgumentError", "LoadError",
            "NameError", "EOFError", "StandardError", "ThreadError",
            "Timeout::Error", "EncodingError", "SystemExit",

            // PHP
            "FatalError", "ParseError", "Notice", "DivisionByZeroError",
            "MemoryLimitExceeded", "PDOException", "ErrorException",
            "InvalidArgument", "OutOfMemoryError", "UnexpectedValueException",

            // C++
            "SegmentationFault", "std::exception", "std::runtime_error", "std::invalid_argument",
            "std::out_of_range", "std::bad_alloc", "MemoryLeak", "StackOverflow", "SIGSEGV", "SIGABRT",
            "std::length_error", "std::overflow_error", "std::underflow_error", "std::domain_error",
            "std::range_error", "std::logic_error",

            // Java
            "NullPointerException", "ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException",
            "IllegalArgumentException", "NumberFormatException", "IllegalStateException",
            "ConcurrentModificationException", "FileNotFoundException", "IOException",
            "ClassCastException", "UnsupportedOperationException", "InterruptedException",
            "SQLException", "ClassNotFoundException", "NoSuchMethodException",
            "InvocationTargetException", "InstantiationException",

            // Kotlin
            "NoSuchElementException", "IndexOutOfBoundsException", "TypeCastException",
            "KotlinNullPointerException", "KotlinIllegalArgumentException",

            // Swift
            "IndexOutOfRange", "UnexpectedNil", "TypeMismatch", "OutOfBounds",
            "UnwrapError", "Segfault", "DivideByZero", "DecodingError", "KeyDecodingError"
    ).stream().map(String::toLowerCase).collect(Collectors.toSet());

    private static final List<String> DELIMITERS = List.of("", "-", "_");

    private WordUtils() {
        //ntd
    }

    /**
     * Starts from a list of words and creates all possible combinations matching all cases and delimiters.
     *
     * @param words the list of words making up a given field name based on the identified casing
     * @return all possible combinations with different casing and delimiters
     */
    public static Set<String> createWordCombinations(String[] words) {
        Set<String> result = new TreeSet<>();

        for (String delimiter : DELIMITERS) {
            result.addAll(progressiveJoin(capitalizeFirstLetter(words), delimiter, String::valueOf));
            result.addAll(progressiveJoin(capitalizeFirstLetter(words), delimiter, StringUtils::uncapitalize));
            result.addAll(progressiveJoin(words, delimiter, String::toLowerCase));
            result.addAll(progressiveJoin(words, delimiter, String::toUpperCase));
        }
        return result;
    }

    private static Set<String> progressiveJoin(String[] words, String delimiter, UnaryOperator<String> function) {
        Set<String> result = new TreeSet<>();

        for (int i = 0; i < words.length; i++) {
            result.add(String.join(delimiter, Arrays.copyOfRange(words, i, words.length)));
        }

        return result.stream().map(function).collect(Collectors.toSet());
    }

    private static String[] capitalizeFirstLetter(String[] words) {
        String[] result = new String[words.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = StringUtils.capitalize(words[i]);
        }

        return result;
    }

    /**
     * Returns the string representation of an object or {@code null} if the object is {@code null}.
     *
     * @param obj The object whose string representation is to be returned.
     * @return The string representation of the object, or {@code null} if the object is {@code null}.
     */
    public static String nullOrValueOf(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    /**
     * Checks if two strings match, disregarding case, by converting them to lowercase using the root locale.
     *
     * @param string1 The first string for comparison.
     * @param string2 The second string for comparison.
     * @return {@code true} if the strings match (ignoring case), {@code false} otherwise.
     * @throws NullPointerException If 'string1' or 'string2' is null.
     */
    public static boolean matchesAsLowerCase(String string1, String string2) {
        return string2.toLowerCase(Locale.ROOT).matches(string1.toLowerCase(Locale.ROOT));
    }

    public static List<String> getKeywordsMatching(String response, Set<String> providedKeywords) {
        if (response == null) {
            return List.of();
        }
        Set<String> toCheck = providedKeywords.isEmpty() ? ERROR_KEYWORDS : providedKeywords;
        return toCheck.parallelStream()
                .filter(response::contains)
                .toList();
    }

    /**
     * Detects the casing of a string based on its format.
     *
     * @param sample The string to detect the casing of.
     * @return The detected casing as a string.
     */
    public static String detectCasingFromString(String sample) {
        if (sample.contains("_") && sample.equals(sample.toUpperCase(Locale.ROOT))) {
            return "UPPER_SNAKE_CASE";
        } else if (sample.contains("_") && sample.equals(sample.toLowerCase(Locale.ROOT))) {
            return "lower_snake_case";
        } else if (sample.contains("-")) {
            return "kebab-case";
        } else if (Character.isLowerCase(sample.charAt(0)) && sample.matches(".*[A-Z].*")) {
            return "camelCase";
        } else if (Character.isUpperCase(sample.charAt(0)) && sample.matches(".*[a-z].*")) {
            return "PascalCase";
        } else if (sample.equals(sample.toLowerCase(Locale.ROOT))) {
            return "lowercase";
        }
        return "UPPER_SNAKE_CASE"; // default
    }

    /**
     * Coverts a string to the detected casing convention.
     *
     * @param name             the string to convert
     * @param casingConvention the casing to convert to
     * @return the converted string
     */
    public static String convertToDetectedCasing(String name, String casingConvention) {
        if (name == null) {
            return null;
        }
        if (casingConvention == null) {
            return name;
        }
        return switch (casingConvention) {
            case "lower_snake_case" -> name.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
                    .toLowerCase(Locale.ROOT);
            case "kebab-case" -> name.replaceAll("([a-z])([A-Z])", "$1-$2")
                    .replaceAll("([A-Z])([A-Z][a-z])", "$1-$2")
                    .toLowerCase(Locale.ROOT);
            case "camelCase" -> Character.toLowerCase(name.charAt(0)) + name.substring(1);
            case "PascalCase" -> name;
            case "lowercase" -> name.toLowerCase(Locale.ROOT);
            default -> name.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
                    .toUpperCase(Locale.ROOT);
        };
    }

    /**
     * Best-effort canonical singular form of a lowercase normalized noun. Intended only for map keys and
     * prefix heuristics. It is not reliable for equality: matching code must use
     * {@link #nounForms(String)} or {@link #sameNoun(String, String)}.
     *
     * @param resource lowercase normalized noun
     * @return best-effort singular form
     */
    public static String singularize(String resource) {
        if (resource == null || resource.isEmpty()) {
            return "";
        }
        String irregular = IRREGULAR_SINGULARS.get(resource);
        if (irregular != null) {
            return irregular;
        }
        int length = resource.length();
        if (resource.endsWith("ies") && length > 3) {
            return resource.substring(0, length - 3) + "y";
        }
        if ((resource.endsWith("sses") || resource.endsWith("shes") || resource.endsWith("ches") ||
                resource.endsWith("xes") || resource.endsWith("zzes")) && length > 3) {
            return resource.substring(0, length - 2);
        }
        if (resource.endsWith("s") && !resource.endsWith("ss") && !resource.endsWith("us") &&
                !resource.endsWith("is") && length > 1) {
            return resource.substring(0, length - 1);
        }
        return resource;
    }

    /**
     * Checks whether a word token is already in a plural form (regular {@code -s} plural or irregular plural).
     *
     * @param token word token
     * @return true if the token is plural
     */
    public static boolean isPlural(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.endsWith("s") || IRREGULAR_SINGULARS.containsKey(lower);
    }

    /**
     * Plausible base forms of a lowercase alphanumeric noun token. The set always includes the token itself and
     * may contain non-words, which is harmless as long as forms are only compared with each other.
     *
     * @param token lowercase alphanumeric token
     * @return unmodifiable set of plausible base forms
     */
    public static Set<String> nounForms(String token) {
        if (token == null || token.isEmpty()) {
            return Set.of();
        }
        Set<String> cached = NOUN_FORMS_CACHE.get(token);
        if (cached != null) {
            return cached;
        }
        Set<String> forms = computeNounForms(token);
        if (NOUN_FORMS_CACHE.size() > MAX_FORMS_CACHE_SIZE) {
            NOUN_FORMS_CACHE.clear();
        }
        NOUN_FORMS_CACHE.put(token, forms);
        return forms;
    }

    private static final List<String> COMPOUND_PREPOSITIONS = List.of(
            "sof", "sfor", "sin", "swithout", "sby", "soff");

    private static Set<String> computeNounForms(String token) {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(token);
        String irregular = IRREGULAR_SINGULARS.get(token);
        if (irregular != null) {
            forms.add(irregular);
        }
        int length = token.length();
        addPrimarySuffixForms(forms, token, length);
        addSibilantAndStandardSuffixForms(forms, token, length);
        addCompoundPrepositionForms(forms, token, length);
        return Collections.unmodifiableSet(forms);
    }

    private static void addPrimarySuffixForms(Set<String> forms, String token, int length) {
        if (token.endsWith("ices") && length > 4) {
            String stem = token.substring(0, length - 4);
            addStem(forms, stem + "ix");
            addStem(forms, stem + "ex");
        }
        if (token.endsWith("ies") && length > 3) {
            addStem(forms, token.substring(0, length - 3) + "y");
        }
        if (token.endsWith("ais") && length > 3) {
            addStem(forms, token.substring(0, length - 3) + "al");
        }
        if (token.endsWith("ves") && length > 3) {
            String stem = token.substring(0, length - 3);
            addStem(forms, stem + "f");
            addStem(forms, stem + "fe");
        }
    }

    private static void addSibilantAndStandardSuffixForms(Set<String> forms, String token, int length) {
        if (isSibilantOrOesPlural(token) && length > 3) {
            addStem(forms, token.substring(0, length - 2));
            if (token.endsWith("ses")) {
                addStem(forms, token.substring(0, length - 2) + "is");
            }
        }
        if (token.endsWith("s") && !token.endsWith("ss") && length > 2) {
            addStem(forms, token.substring(0, length - 1));
        }
    }

    private static boolean isSibilantOrOesPlural(String token) {
        return token.endsWith("ses") || token.endsWith("xes") || token.endsWith("zes") ||
                token.endsWith("ches") || token.endsWith("shes") || token.endsWith("oes");
    }

    private static void addCompoundPrepositionForms(Set<String> forms, String token, int length) {
        if (length <= 6) {
            return;
        }
        for (String prep : COMPOUND_PREPOSITIONS) {
            int idx = token.indexOf(prep);
            if (idx >= 3 && idx + prep.length() + 2 <= length) {
                String head = token.substring(0, idx + 1);
                String tail = token.substring(idx + 1);
                for (String headForm : computeSimpleSingulars(head)) {
                    addStem(forms, headForm + tail);
                }
            }
        }
    }

    private static Set<String> computeSimpleSingulars(String head) {
        Set<String> out = new LinkedHashSet<>();
        int len = head.length();
        if (head.endsWith("ies") && len > 3) {
            out.add(head.substring(0, len - 3) + "y");
        }
        if ((head.endsWith("ses") || head.endsWith("xes") || head.endsWith("zes") ||
                head.endsWith("ches") || head.endsWith("shes")) && len > 3) {
            out.add(head.substring(0, len - 2));
        }
        if (head.endsWith("s") && !head.endsWith("ss") && len > 2) {
            out.add(head.substring(0, len - 1));
        }
        return out;
    }

    private static void addStem(Set<String> forms, String stem) {
        if (stem.length() >= 2) {
            forms.add(stem);
        }
    }

    /**
     * Checks whether two tokens can be forms of the same noun, i.e. their form sets intersect.
     *
     * @param first  first lowercase alphanumeric token
     * @param second second lowercase alphanumeric token
     * @return true when the tokens may refer to the same noun
     */
    public static boolean sameNoun(String first, String second) {
        Set<String> firstForms = nounForms(first);
        Set<String> secondForms = nounForms(second);
        if (!Collections.disjoint(firstForms, secondForms)) {
            return true;
        }
        for (String f : firstForms) {
            for (String s : secondForms) {
                if (matchesWithInsertedMiddleQualifier(f, s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether two multi-word normalized nouns share both a leading modifier ({@code >= 4} chars)
     * and a trailing head noun ({@code >= 4} chars) that together cover the shorter noun
     * (e.g. {@code formcolumn} vs {@code formviewcolumn}, {@code emailmethod} vs {@code emailauthenticationmethod}).
     */
    public static boolean matchesWithInsertedMiddleQualifier(String first, String second) {
        if (first == null || second == null || first.length() == second.length()) {
            return false;
        }
        String shorter = first.length() < second.length() ? first : second;
        String longer = first.length() < second.length() ? second : first;
        if (shorter.length() < 7) {
            return false;
        }
        int prefixLen = 0;
        while (prefixLen < shorter.length() && shorter.charAt(prefixLen) == longer.charAt(prefixLen)) {
            prefixLen++;
        }
        int suffixLen = 0;
        while (suffixLen < shorter.length() &&
                shorter.charAt(shorter.length() - 1 - suffixLen) == longer.charAt(longer.length() - 1 - suffixLen)) {
            suffixLen++;
        }
        return prefixLen >= 3 && suffixLen >= 4 &&
                prefixLen < shorter.length() && suffixLen < shorter.length() &&
                prefixLen + suffixLen >= shorter.length();
    }

    /**
     * Strips all non-ASCII-alphanumeric characters and lowercases ASCII letters in a single pass.
     *
     * @param value raw string (may be null)
     * @return lowercase ASCII alphanumeric string, never null
     */
    public static String normalizeAlphaNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + ('a' - 'A')));
            }
        }
        return sb.toString();
    }

    /**
     * Splits a raw identifier or path segment on camelCase, acronym, and non-alphanumeric boundaries.
     *
     * @param rawName raw identifier
     * @return non-empty word tokens
     */
    public static List<String> splitWords(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return List.of();
        }
        return WORD_BOUNDARY_SPLITTER.splitToList(rawName.trim());
    }

    /**
     * Extracts the leaf property name after the last {@code '#'} or {@code '.'} delimiter and normalizes it
     * to lowercase ASCII alphanumeric characters.
     *
     * @param propertyPath property path or name
     * @return normalized leaf property name
     */
    public static String normalizeLeaf(String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return "";
        }
        int lastSep = Math.max(propertyPath.lastIndexOf('#'), propertyPath.lastIndexOf('.'));
        String leaf = lastSep >= 0 ? propertyPath.substring(lastSep + 1) : propertyPath;
        return normalizeAlphaNumeric(leaf);
    }

    /**
     * Checks whether a raw or normalized field name represents an audit timestamp (such as {@code createdAt},
     * {@code updatedDate}, {@code lastModifiedTime}, {@code modified_on}).
     *
     * @param rawKey property name
     * @return true when the field name follows an audit-timestamp naming pattern
     */
    public static boolean isAuditTimestampField(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return false;
        }
        String normalized = normalizeAlphaNumeric(rawKey);
        if (AUDIT_TIMESTAMP_NORMALIZED.contains(normalized)) {
            return true;
        }
        List<String> words = WORD_BOUNDARY_SPLITTER.splitToList(rawKey.trim());
        if (words.size() < 2) {
            return false;
        }
        String first = words.getFirst().toLowerCase(Locale.ROOT);
        String last = words.getLast().toLowerCase(Locale.ROOT);
        return AUDIT_TIMESTAMP_PREFIX_WORDS.contains(first) && AUDIT_TIMESTAMP_SUFFIX_WORDS.contains(last);
    }
}
