package com.endava.cats.model;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** The exact HTTP status alternatives declared by a test's response oracle, independent of its prose. */
public record ExpectedResponse(List<String> codes) {
    private static final Pattern HTTP_CODE = Pattern.compile("[1-5](?:[0-9]{2}|XX)");
    private static final List<String> ALL_FAMILIES = List.of("1XX", "2XX", "3XX", "4XX", "5XX");
    private static final Pattern CODE_DECLARATION = Pattern.compile("[1-5](?:[0-9]{2}|XX)(?:\\s*[,|]\\s*[1-5](?:[0-9]{2}|XX))*");

    public ExpectedResponse {
        codes = Optional.ofNullable(codes).orElse(List.of()).stream().filter(Objects::nonNull).distinct().toList();
    }

    /** Reads only the executor's dedicated code declaration; arbitrary expectation prose remains unknown. */
    public static ExpectedResponse fromCodeDeclaration(String declaration) {
        String codes = Optional.ofNullable(declaration).orElse("").strip();
        if (codes.startsWith("[") && codes.endsWith("]")) {
            codes = codes.substring(1, codes.length() - 1).strip();
        }
        return CODE_DECLARATION.matcher(codes).matches()
                ? new ExpectedResponse(Arrays.asList(codes.split("\\s*[,|]\\s*"))) : new ExpectedResponse(List.of());
    }

    /** @return the HTTP outcome category; the exact codes remain authoritative for mixed or unusual expectations */
    public Outcome outcome() {
        if (codes.isEmpty() || codes.stream().anyMatch(code -> !HTTP_CODE.matcher(code).matches())) {
            return Outcome.UNKNOWN;
        }
        if (codes.stream().allMatch(code -> code.startsWith("2"))) {
            return Outcome.SUCCESS;
        }
        if (codes.stream().allMatch(code -> code.startsWith("4"))) {
            return Outcome.REJECTION;
        }
        return codes.containsAll(ALL_FAMILIES) ? Outcome.ANY : Outcome.OTHER;
    }

    /** HTTP expectations only; this does not describe additional schema, security or behavioral assertions. */
    public enum Outcome {
        UNKNOWN, SUCCESS, REJECTION, ANY, OTHER
    }
}
