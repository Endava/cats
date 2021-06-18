package com.endava.cats.util;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class CatsDSLParser {
    private static final Map<String, Parser> PARSERS = ImmutableMap.of("T(java.time", new SimpleParser(), "T(org.apache.commons.lang3", new SimpleParser(),
            "T(java.util", new SimpleParser());

    public String parseAndGetResult(String valueFromFile, String jsonPayload) {

        Optional<Map.Entry<String, Parser>> parserEntry = PARSERS.entrySet().stream().filter(entry -> valueFromFile.startsWith(entry.getKey())).findAny();
        if (parserEntry.isPresent()) {
            return parserEntry.get().getValue().parse(valueFromFile, jsonPayload);
        }

        return valueFromFile;
    }
}
