package com.endava.cats.generator.format;

import com.endava.cats.generator.format.impl.*;

import java.util.stream.Stream;

/**
 * Keeps a mapping between OpenAPI format types and CATs generators
 */
public enum FormatGenerator {
    BYTE("byte", new ByteFormatGeneratorStrategy()),
    DATE("date", new DateFormatGeneratorStrategy()),
    DATE_TIME("date-time", new DateTimeFormatGeneratorStrategy()),
    EMAIL("email", new EmailFormatGeneratorStrategy()),
    HOSTNAME("hostname", new HostnameFormatGeneratorStrategy()),
    IP("ip", new IPV4FormatGenerationStrategy()),
    IPV4("ipv4", new IPV4FormatGenerationStrategy()),
    IPV6("ipv6", new IPV6FormatGenerationStrategy()),
    PWD("password", new PasswordFormatGeneratorStrategy()),
    URI("uri", new URIFormatGeneratorStrategy()),
    URL("url", new URLFormatGeneratorStrategy()),
    UUID("uuid", new UUIDFormatGeneratorStrategy()),

    SKIP("", new NoFormatGenerationStrategy());

    private String format;
    private FormatGeneratorStrategy generatorStrategy;

    FormatGenerator(String format, FormatGeneratorStrategy strategy) {
        this.format = format;
        this.generatorStrategy = strategy;
    }

    public static FormatGenerator from(String format) {
        return Stream.of(values()).filter(value -> value.format.equalsIgnoreCase(format))
                .findFirst().orElse(SKIP);
    }

    public FormatGeneratorStrategy getGeneratorStrategy() {
        return generatorStrategy;
    }
}
