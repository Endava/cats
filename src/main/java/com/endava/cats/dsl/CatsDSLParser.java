package com.endava.cats.dsl;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

@Singleton
public class CatsDSLParser {
    private static final Map<String, Parser> PARSERS = Map.of("T(", new SpringELParser(), "$$", new EnvVariableParser(),
            "$request", new RequestVariableParser());

    public String parseAndGetResult(String valueFromFile, String jsonPayload) {

        Optional<Map.Entry<String, Parser>> parserEntry = PARSERS.entrySet().stream().filter(entry -> valueFromFile.startsWith(entry.getKey())).findAny();
        if (parserEntry.isPresent()) {
            return parserEntry.get().getValue().parse(valueFromFile, jsonPayload);
        }

        return valueFromFile;
    }

    public String getParseContext(String expression, String request, String response) {
        if (expression.startsWith("$request")) {
            return request;
        }
        return response;
    }
}
