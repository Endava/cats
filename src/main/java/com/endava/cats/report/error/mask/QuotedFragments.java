package com.endava.cats.report.error.mask;

import com.endava.cats.http.HttpMethod;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The quote policy of error-template masking. A quoted fragment is kept when it looks like a code identifier and
 * masked to {@code 'STR'} / {@code "STR"} otherwise, with the same rule for both quote styles:
 * <ul>
 *     <li>Java names the failing code in double quotes: {@code Cannot invoke "String.length()" because "val" is null};</li>
 *     <li>JavaScript, Python, Go and Fastify name it in single quotes: {@code (reading 'id')}, {@code KeyError: 'name'},
 *     {@code Field validation for 'Name' failed on the 'required' tag}.</li>
 * </ul>
 * Quoted identifiers are also <em>pinned</em>: the template miner never turns them into a wildcard, because two
 * messages that only differ by such an identifier come from two different crash sites or validation rules.
 * <p>
 * Sent values are masked to {@code <INPUT>} before this policy applies, so an identifier-like value echoed back by the
 * server is not kept. HTTP method names (all methods CATS sends, WebDAV included) are request data, not code, and are
 * masked.
 */
public final class QuotedFragments {
    static final String MASKED = "STR";
    private static final Pattern SINGLE_QUOTED = Pattern.compile("(?<![\\w'])'([^'\\n]{1,120})'(?![\\w'])");
    private static final Pattern DOUBLE_QUOTED = Pattern.compile("(?<![\\w\"])\"([^\"\\n]{1,200})\"(?![\\w\"])");
    private static final Pattern QUOTED_TOKEN = Pattern.compile("[^'\"]*(['\"])([^'\"]+)\\1[^'\"]*");
    private static final Pattern IDENTIFIER_HEAD = Pattern.compile("[A-Za-z_$][\\w$]*|<[\\w$]+>");
    private static final Pattern IDENTIFIER_PART = Pattern.compile("[\\w$]+|<[\\w$]+>");
    private static final Pattern ARGUMENTS = Pattern.compile("\\([\\w$.,\\[\\] ]*\\)");
    private static final int MIN_IDENTIFIER_LENGTH = 2;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final Set<String> HTTP_METHODS = Arrays.stream(HttpMethod.values()).map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> PLACEHOLDERS = Set.of(MASKED, "NUM", "PATH", "URL", "UUID", "HASH", "TIMESTAMP");

    private QuotedFragments() {
    }

    /**
     * Masks every quoted fragment that is not a code identifier.
     *
     * @param text the text to mask
     * @return the text with {@code 'STR'} / {@code "STR"} in place of quoted data
     */
    static String maskData(String text) {
        return maskData(maskData(text, SINGLE_QUOTED, "'"), DOUBLE_QUOTED, "\"");
    }

    private static String maskData(String text, Pattern quoted, String quote) {
        return quoted.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                isCodeIdentifier(match.group(1)) ? match.group() : quote + MASKED + quote));
    }

    /**
     * Tells whether a token holds a quoted code identifier, e.g. {@code 'id')} or {@code "String.length()"}. Masking
     * placeholders such as {@code '<INPUT>'} or {@code 'NUM'} are not pinned.
     *
     * @param token a masked message token
     * @return {@code true} if the template miner must never generalize the token
     */
    public static boolean isPinned(String token) {
        Matcher matcher = QUOTED_TOKEN.matcher(token);
        return matcher.matches() && isCodeIdentifier(matcher.group(2)) && !isPlaceholder(matcher.group(2));
    }

    private static boolean isCodeIdentifier(String quoted) {
        return quoted.length() >= MIN_IDENTIFIER_LENGTH && quoted.length() <= MAX_IDENTIFIER_LENGTH
                && !HTTP_METHODS.contains(quoted) && matchesCodeIdentifier(quoted);
    }

    private static boolean matchesCodeIdentifier(String quoted) {
        int arguments = quoted.indexOf('(');
        if (arguments >= 0 && !ARGUMENTS.matcher(quoted.substring(arguments)).matches()) {
            return false;
        }
        String name = arguments < 0 ? quoted : quoted.substring(0, arguments);
        String[] parts = name.split("\\.", -1);
        return IDENTIFIER_HEAD.matcher(parts[0]).matches()
                && Arrays.stream(parts).skip(1).allMatch(part -> IDENTIFIER_PART.matcher(part).matches());
    }

    private static boolean isPlaceholder(String identifier) {
        return identifier.startsWith("<") || PLACEHOLDERS.contains(identifier);
    }
}
