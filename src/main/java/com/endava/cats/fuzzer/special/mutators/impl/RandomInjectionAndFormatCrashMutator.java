package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Replaces the target field with parser, query, expression, format-string, path-traversal,
 * and out-of-range temporal payloads designed to trigger backend exceptions.
 */
@Singleton
public class RandomInjectionAndFormatCrashMutator implements BodyMutator {
    private static final List<String> CRASH_PAYLOADS = List.of(
            "' OR 1=1--",
            "'; SELECT 1;--",
            "\\';",
            "${7*7}",
            "#{7*7}",
            "{{7*7}}",
            "%s%s%s%s%n%n",
            "%x%x%d%n",
            "{0}{1}{-1}",
            "../../../../etc/passwd",
            "..\\..\\..\\..\\windows\\win.ini",
            "file:///etc/passwd",
            "http://127.0.0.1:0/",
            "://",
            "[(",
            "\u0000",
            "%00",
            "0000-00-00T00:00:00Z",
            "9999-99-99T99:99:99Z",
            "1970-01-01T25:61:61Z",
            "-999999999-01-01");

    @Override
    public String mutate(String inputJson, String selectedField) {
        String value = CRASH_PAYLOADS.get(CatsRandom.instance().nextInt(CRASH_PAYLOADS.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, value).json();
    }

    @Override
    public String description() {
        return "replace field with injection, expression, format-string, URI, and temporal crash payloads";
    }
}
