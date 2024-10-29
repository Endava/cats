package com.endava.cats.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Helper class for words operations.
 */
public abstract class WordUtils {
    private static final Set<String> ERROR_KEYWORDS = Set.of(
            "StackTrace", "BadRequest", "InternalServerError", "Unauthorized",
            "Forbidden", "ServiceUnavailable", "Timeout", "PermissionDenied", "InvalidToken",
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
            "FatalError", "ParseError", "Warning", "Notice", "DivisionByZeroError",
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
    );

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

    public static List<String> getKeywordsMatching(String response) {
        if (response == null) {
            return List.of();
        }
        return ERROR_KEYWORDS.stream()
                .filter(keyword -> response.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .toList();
    }
}