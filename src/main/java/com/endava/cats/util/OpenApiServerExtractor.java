package com.endava.cats.util;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts all possible server URLs from an OpenAPI specification.
 * Handles server variables by generating all combinations.
 */
public class OpenApiServerExtractor {

    private OpenApiServerExtractor() {
        //ntd
    }

    /**
     * Extracts all possible server URLs from an OpenAPI specification.
     * Handles server variables by generating all combinations.
     *
     * @param openAPI the parsed OpenAPI object
     * @return list of all possible server URLs
     */
    public static List<String> getServerUrls(OpenAPI openAPI) {
        List<String> serverUrls = new ArrayList<>();

        if (openAPI.getServers() == null || openAPI.getServers().isEmpty()) {
            return serverUrls;
        }

        for (Server server : openAPI.getServers()) {
            String urlTemplate = server.getUrl();
            ServerVariables variables = server.getVariables();

            if (variables == null || variables.isEmpty()) {
                serverUrls.add(urlTemplate);
            } else {
                List<String> expandedUrls = expandServerUrl(urlTemplate, variables);
                serverUrls.addAll(expandedUrls);
            }
        }

        return serverUrls;
    }

    /**
     * Expands a server URL template with all possible variable combinations.
     *
     * @param urlTemplate the URL template with variables (e.g., "https://{environment}.example.com")
     * @param variables   the server variables definition
     * @return list of all expanded URLs
     */
    private static List<String> expandServerUrl(String urlTemplate, ServerVariables variables) {
        List<Map<String, String>> allCombinations = generateVariableCombinations(variables);
        List<String> expandedUrls = new ArrayList<>();

        for (Map<String, String> combination : allCombinations) {
            String expandedUrl = urlTemplate;
            for (Map.Entry<String, String> entry : combination.entrySet()) {
                expandedUrl = expandedUrl.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            expandedUrls.add(expandedUrl);
        }

        return expandedUrls;
    }

    /**
     * Generates all possible combinations of server variable values.
     *
     * @param variables the server variables
     * @return list of all possible variable value combinations
     */
    private static List<Map<String, String>> generateVariableCombinations(ServerVariables variables) {
        List<Map<String, String>> combinations = new ArrayList<>();
        combinations.add(new HashMap<>());

        for (Map.Entry<String, ServerVariable> entry : variables.entrySet()) {
            String varName = entry.getKey();
            List<String> possibleValues = getPossibleValues(entry);

            if (possibleValues.isEmpty()) {
                continue;
            }

            // Create new combinations for each possible value
            List<Map<String, String>> newCombinations = new ArrayList<>();
            for (Map<String, String> existingCombo : combinations) {
                for (String value : possibleValues) {
                    Map<String, String> newCombo = new HashMap<>(existingCombo);
                    newCombo.put(varName, value);
                    newCombinations.add(newCombo);
                }
            }
            combinations = newCombinations;
        }

        return combinations;
    }

    private static List<String> getPossibleValues(Map.Entry<String, ServerVariable> entry) {
        ServerVariable variable = entry.getValue();

        List<String> possibleValues = new ArrayList<>();

        if (variable.getDefault() != null) {
            possibleValues.add(variable.getDefault());
        }

        if (variable.getEnum() != null && !variable.getEnum().isEmpty()) {
            for (String enumValue : variable.getEnum()) {
                if (!possibleValues.contains(enumValue)) {
                    possibleValues.add(enumValue);
                }
            }
        }
        return possibleValues;
    }
}
