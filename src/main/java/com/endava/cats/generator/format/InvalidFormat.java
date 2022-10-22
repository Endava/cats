package com.endava.cats.generator.format;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidByteFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidDateFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidDateTimeFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidEmailFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidHostnameFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidIPV4FormatGenerator;
import com.endava.cats.generator.format.impl.InvalidIPV6FormatGenerator;
import com.endava.cats.generator.format.impl.NoFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidPasswordFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidURIFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidURLFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidUUIDFormatGenerator;

import java.util.stream.Stream;

/**
 * Keeps a mapping between OpenAPI format types and CATs generators
 */
public enum InvalidFormat {
    BYTE("byte", new InvalidByteFormatGenerator()),
    DATE("date", new InvalidDateFormatGenerator()),
    DATE_TIME("date-time", new InvalidDateTimeFormatGenerator()),
    EMAIL("email", new InvalidEmailFormatGenerator()),
    HOSTNAME("hostname", new InvalidHostnameFormatGenerator()),
    IP("ip", new InvalidIPV4FormatGenerator()),
    IPV4("ipv4", new InvalidIPV4FormatGenerator()),
    IPV6("ipv6", new InvalidIPV6FormatGenerator()),
    PWD("password", new InvalidPasswordFormatGenerator()),
    URI("uri", new InvalidURIFormatGenerator()),
    URL("url", new InvalidURLFormatGenerator()),
    UUID("uuid", new InvalidUUIDFormatGenerator()),

    SKIP("", new NoFormatGenerator());

    private final String format;
    private final InvalidFormatGenerator generatorStrategy;

    InvalidFormat(String format, InvalidFormatGenerator strategy) {
        this.format = format;
        this.generatorStrategy = strategy;
    }

    public static InvalidFormat from(String format) {
        return Stream.of(values()).filter(value -> value.format.equalsIgnoreCase(format))
                .findFirst().orElse(SKIP);
    }

    public InvalidFormatGenerator getGeneratorStrategy() {
        return generatorStrategy;
    }
}
