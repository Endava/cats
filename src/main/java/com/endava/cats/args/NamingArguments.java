package com.endava.cats.args;

import lombok.Getter;
import picocli.CommandLine;

import javax.inject.Singleton;
import java.util.regex.Pattern;

@Singleton
@Getter
public class NamingArguments {

    @CommandLine.Option(names = {"--pathNaming"},
            description = "Naming strategy for paths (excluding path variables). Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@,  @|bold,underline PASCAL|@,  @|bold,underline CAMEL|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming pathNaming = Naming.KEBAB;

    @CommandLine.Option(names = {"--pathVariablesNaming"},
            description = "Naming strategy for paths variables. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@,  @|bold,underline PASCAL|@,  @|bold,underline CAMEL|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming pathVariablesNaming = Naming.CAMEL;

    @CommandLine.Option(names = {"--queryParamsNaming"},
            description = "Naming strategy for query parameters. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@,  @|bold,underline PASCAL|@,  @|bold,underline CAMEL|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming queryParamsNaming = Naming.SNAKE;

    @CommandLine.Option(names = {"--jsonObjectsNaming"},
            description = "Naming strategy for json objects. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@,  @|bold,underline PASCAL|@,  @|bold,underline CAMEL|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming jsonObjectsNaming = Naming.PASCAL;

    @CommandLine.Option(names = {"--jsonPropertiesNaming"},
            description = "Naming strategy for json object properties. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@,  @|bold,underline PASCAL|@,  @|bold,underline CAMEL|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming jsonPropertiesNaming = Naming.CAMEL;

    @CommandLine.Option(names = {"--headersNaming"},
            description = "Naming strategy for json object properties. Possible values @|bold,underline SNAKE|@, @|bold,underline KEBAB|@,  @|bold,underline PASCAL|@,  @|bold,underline CAMEL|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private Naming headersNaming = Naming.HTTP_HEADER;


    public enum Naming {
        SNAKE("^[a-z]+((_)?[a-z])*+$", "snake_case"),
        KEBAB("^[a-z]+((-)?[a-z])*+$", "kebab-case"),
        HTTP_HEADER("^[A-Z][-a-zA-Z0-9]*(?<!-)$", "Http-Case"),
        PASCAL("^[A-Z][A-Za-z0-9]+$", "PascalCase"),
        CAMEL("^[a-z]+[A-Za-z0-9]+$", "camelCase");

        @Getter
        private final Pattern pattern;
        @Getter
        private final String description;

        Naming(String namingPattern, String description) {
            this.pattern = Pattern.compile(namingPattern);
            this.description = description;
        }
    }
}
