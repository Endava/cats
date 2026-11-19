package com.endava.cats.fuzzer.fields;

import com.endava.cats.util.JsonUtils;

import java.util.List;
import java.util.stream.Stream;

/** Exact, bounded decimal representations and malformed number grammar; no local numeric conversion is performed. */
final class NumericParserInputs {
    private static final List<String> TOKENS = List.of("0", "-0.0", "2.2250738585072012e-308", "2.2250738585072011e-308",
            "4.9406564584124654e-324", "1.7976931348623157e308", "1.7976931348623159e308", "9007199254740993",
            "1e10000", "1e-10000", "1e-2147483648", "1" + "0".repeat(1000), "0." + "0".repeat(1000) + "1",
            "01", "+1", "1.", "1e", "1e+", "--1");
    private static final List<String> VALUES = TOKENS.stream().flatMap(value -> Stream.of(value, JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(value))).toList();

    private NumericParserInputs() {
    }

    static List<String> values() {
        return VALUES;
    }
}
