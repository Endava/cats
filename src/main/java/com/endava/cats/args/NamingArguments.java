package com.endava.cats.args;

import jakarta.inject.Singleton;
import lombok.Getter;
import picocli.CommandLine;

import java.util.regex.Pattern;

/**
 * Holds arguments related to naming conventions when linting OpenAPI specs.
 */
@Singleton
@Getter
public class NamingArguments {

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
        CAMEL("^[a-z]+[A-Za-z0-9]+$", "camelCase");

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
