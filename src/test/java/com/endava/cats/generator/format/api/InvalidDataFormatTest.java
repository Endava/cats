package com.endava.cats.generator.format.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

@QuarkusTest
class InvalidDataFormatTest {

    @Inject
    InvalidDataFormat invalidDataFormat;

    @Test
    void shouldReturnVoidWhenNoFormat() {
        Assertions.assertThat(invalidDataFormat.generator(new Schema<>(), null)).isInstanceOf(VoidGenerator.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "isin", "iso6166", "lei", "iso17442", "aba", "routing-number", "rtn",
            "mcc", "iso18245", "ulid", "jwt", "jws", "base64url", "sha256", "sha-256",
            "arn", "aws-arn", "imei", "orcid", "doi",
            "char", "date-time-local", "decimal", "decimal128", "http-date", "ipv4-cidr", "ipv6-cidr",
            "media-range", "time-local", "sf-binary", "sf-boolean", "sf-string", "sf-token",
            "iban", "bic", "swift", "cidr", "phone", "e164", "time", "timeZone", "cron",
            "json-pointer", "relative-json-pointer", "uri-template", "uri-reference",
            "iri", "iri-reference", "idn-email", "idn-hostname",
            "isbn13", "isbn10", "isbn", "gtin13", "ean13", "gtin8", "ean8",
            "content-type", "language", "unixtime"
    })
    void shouldReturnUniqueInvalidGeneratorWithNonBlankValues(String format) {
        Schema<?> schema = new Schema<>();
        schema.setFormat(format);
        List<InvalidDataFormatGenerator> generators = invalidDataFormat.getGenerators(schema, "");
        Assertions.assertThat(generators).hasSize(1);
        InvalidDataFormatGenerator generator = generators.getFirst();
        Assertions.assertThat(generator).isNotInstanceOf(VoidGenerator.class);
        Assertions.assertThat(generator.getAlmostValidValue()).isNotBlank();
        Assertions.assertThat(generator.getTotallyWrongValue()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({
            "date-time-local,createdAt",
            "time-local,arrivalTime",
            "http-date,modifiedAt",
            "ipv6-cidr,ipv6Cidr",
            "ipv4-cidr,clientIp",
            "base64url,base64Url",
            "uri-reference,avatarUri",
            "uri-template,endpointUrl",
            "iri,profileUrl",
            "idn-email,userEmail"
    })
    void shouldNotClashForInvalidGeneratorOnCombinedFormatAndProperty(String format, String propertyName) {
        Schema<?> schema = new Schema<>();
        schema.setFormat(format);
        Assertions.assertThat(invalidDataFormat.getGenerators(schema, propertyName)).hasSize(1);
    }
}
