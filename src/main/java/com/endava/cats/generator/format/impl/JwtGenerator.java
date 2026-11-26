package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Generates structurally valid and invalid RFC 7519 JSON Web Tokens (JWT / JWS compact serialization).
 */
@Singleton
public class JwtGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> FORMATS = Set.of("jwt", "jws");
    private static final List<String> PROPERTY_SUFFIXES = List.of("jwt", "jwttoken", "idtoken", "accesstoken", "refreshtoken", "jws");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || PROPERTY_SUFFIXES.stream().anyMatch(sanitizedProperty::endsWith);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("jwt", "jws");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String claims = "{\"sub\":\"%s\",\"iat\":%d}".formatted(CatsRandom.alphanumeric(10), Instant.now().getEpochSecond());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = new byte[32];
        CatsRandom.instance().nextBytes(signatureBytes);
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        return DataFormat.matchesPatternOrNull(schema, HEADER + "." + payload + "." + signature);
    }

    @Override
    public String getAlmostValidValue() {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not.a.valid+jwt/token==";
    }
}
