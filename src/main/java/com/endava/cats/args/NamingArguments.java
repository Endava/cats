package com.endava.cats.args;

import com.endava.cats.exception.CatsException;
import com.endava.cats.util.AnsiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Holds arguments related to naming conventions when linting OpenAPI specs.
 */
@Singleton
@Getter
public class NamingArguments {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    @CommandLine.Option(names = {"--pathNaming"},
            description = "Naming strategy for paths (excluding path variables). Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@, @|bold,underline PASCAL|@, @|bold,underline CAMEL|@, @|bold,underline HTTP_HEADER|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming pathNaming = Naming.KEBAB;

    @CommandLine.Option(names = {"--pathVariablesNaming"},
            description = "Naming strategy for path variables. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@, @|bold,underline PASCAL|@, @|bold,underline CAMEL|@, @|bold,underline HTTP_HEADER|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming pathVariablesNaming = Naming.CAMEL;

    @CommandLine.Option(names = {"--queryParamsNaming"},
            description = "Naming strategy for query parameters. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@, @|bold,underline PASCAL|@, @|bold,underline CAMEL|@, @|bold,underline HTTP_HEADER|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming queryParamsNaming = Naming.SNAKE;

    @CommandLine.Option(names = {"--jsonObjectsNaming"},
            description = "Naming strategy for json objects. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@, @|bold,underline PASCAL|@, @|bold,underline CAMEL|@, @|bold,underline HTTP_HEADER|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming jsonObjectsNaming = Naming.PASCAL;

    @CommandLine.Option(names = {"--jsonPropertiesNaming"},
            description = "Naming strategy for json object properties. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@, @|bold,underline PASCAL|@, @|bold,underline CAMEL|@, @|bold,underline HTTP_HEADER|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming jsonPropertiesNaming = Naming.CAMEL;

    @CommandLine.Option(names = {"--headersNaming"},
            description = "Naming strategy for HTTP headers. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@, @|bold,underline PASCAL|@, @|bold,underline CAMEL|@, @|bold,underline HTTP_HEADER|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming headersNaming = Naming.HTTP_HEADER;

    @CommandLine.Option(names = {"--enumsNaming"},
            description = "Naming strategy for enums. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming enumsNaming = Naming.UPPER_UNDERSCORE;

    @CommandLine.Option(names = {"--operationPrefixMapFile"},
            description = "Path to the file containing operationId prefix mappings.")
    private File operationPrefixMapFile;

    @CommandLine.Option(names = {"--strict"},
            description = "Use strict checks. When using strict checks all warnings will be reported as errors. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean strict;

    private Map<String, List<String>> verbMappings;

    public void loadVerbMapFile() {
        verbMappings = new HashMap<>(defaultVerbMappings());

        if (operationPrefixMapFile == null) {
            log.debug("No operationId prefix mapping config file provided! Using default mappings.");
            return;
        }

        log.config(AnsiUtils.bold("Loading operationId prefix mapping custom configuration: {}"),
                AnsiUtils.blue(operationPrefixMapFile));

        try (InputStream stream = new FileInputStream(operationPrefixMapFile)) {
            Properties verbMappingAsProperties = new Properties();
            verbMappingAsProperties.load(stream);

            verbMappingAsProperties.forEach((key, value) -> {
                String keyStr = (String) key;
                String valueStr = (String) value;

                if (StringUtils.isNotBlank(valueStr)) {
                    List<String> mappedVerbs = Arrays.stream(valueStr.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();

                    if (!mappedVerbs.isEmpty()) {
                        verbMappings.put(keyStr.toLowerCase(Locale.ROOT), mappedVerbs);
                    }
                }
            });
        } catch (Exception e) {
            throw new CatsException(e);
        }
    }

    private static Map<String, List<String>> defaultVerbMappings() {
        return Map.of(
                "get", List.of("get", "fetch", "list", "retrieve", "find", "read", "query", "search", "load", "use", "obtain", "acquire"),
                "post", List.of("create", "add", "post", "insert", "push", "submit", "send", "upsert", "do", "execute", "trigger", "perform", "process", "invoke", "call", "start", "initiate"),
                "put", List.of("update", "replace", "put", "modify", "set", "change", "alter", "upsert", "adjust", "reconfigure", "rebuild", "recreate", "refresh", "reinitialize"),
                "delete", List.of("delete", "remove", "destroy", "purge", "drop", "erase", "clear", "discard", "deactivate", "uninstall", "withdraw", "revoke"),
                "patch", List.of("patch", "modify", "update", "alter", "change", "set", "upsert", "adjust", "edit", "revise", "tweak", "refine", "improve")
        );
    }

    public Map<String, List<String>> getOperationPrefixMappings() {
        return verbMappings;
    }


    /**
     * Enumerates different naming conventions along with their corresponding regular expression patterns and descriptions.
     */
    @Getter
    public enum Naming {
        /**
         * Represents the snake_case naming convention.
         */
        SNAKE("^[a-z]+((_)?[a-z])*+$", "snake_case"),
        /**
         * Represents the kebab-case naming convention.
         */
        KEBAB("^[a-z]+((-)?[a-z])*+$", "kebab-case"),
        /**
         * Represents the HTTP header naming convention.
         */
        HTTP_HEADER("^[A-Z][-a-zA-Z0-9]*(?<!-)$", "Http-Case"),
        /**
         * Represents the PascalCase naming convention.
         */
        PASCAL("^[A-Z][A-Za-z0-9]+$", "PascalCase"),
        /**
         * Represents the camelCase naming convention.
         */
        CAMEL("^[a-z]+[A-Za-z0-9]+$", "camelCase"),

        /**
         * Represents the UPPER_UNDERSCORE naming convention.
         * This is typically used for constants in programming languages.
         */
        UPPER_UNDERSCORE("^[A-Z]+(_[A-Z]+)*$", "UPPER_UNDERSCORE"),

        /**
         * Represents the lower_underscore naming convention.
         * This is typically used for variable names in programming languages.
         */
        LOWER_UNDERSCORE("^[a-z]+(_[a-z]+)*$", "lower_underscore");

        private final Pattern pattern;
        private final String description;

        /**
         * Constructs a new instance of the {@code Naming} enum with the specified naming pattern and description.
         *
         * @param namingPattern the regular expression pattern for the naming convention
         * @param description   the human-readable description of the naming convention
         */
        Naming(String namingPattern, String description) {
            this.pattern = Pattern.compile(namingPattern);
            this.description = description;
        }
    }
}
