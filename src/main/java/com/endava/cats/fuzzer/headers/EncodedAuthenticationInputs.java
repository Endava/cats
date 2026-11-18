package com.endava.cats.fuzzer.headers;

import com.endava.cats.generator.simple.EncodedAuthenticationGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Selects encoded authentication envelopes without logging in or guessing that opaque bearer tokens are JWTs. */
final class EncodedAuthenticationInputs {

    private EncodedAuthenticationInputs() {
    }

    enum Scheme {
        BASIC, JWT
    }

    static List<Scheme> schemes(FuzzingData data, String configured, boolean basicAuth) {
        Stream<String> headers = Optional.ofNullable(data.getHeaders()).orElseGet(java.util.Set::of).stream()
                .filter(header -> "Authorization".equalsIgnoreCase(header.getName())).map(CatsHeader::getValue);
        Stream<Scheme> supplied = Stream.concat(Stream.of(configured), headers).flatMap(EncodedAuthenticationInputs::fromHeader);
        Stream<Scheme> declared = requirements(data).stream().flatMap(requirement -> requirement.keySet().stream())
                .map(name -> securitySchemes(data).get(name)).flatMap(EncodedAuthenticationInputs::fromDeclaration);
        return Stream.concat(Stream.concat(supplied, declared), basicAuth ? Stream.of(Scheme.BASIC) : Stream.empty()).distinct().sorted().toList();
    }

    private static Stream<Scheme> fromHeader(String value) {
        if (org.apache.commons.lang3.Strings.CI.startsWith(value, "Basic ")) {
            return Stream.of(Scheme.BASIC);
        }
        if (org.apache.commons.lang3.Strings.CI.startsWith(value, "Bearer ") && value.substring(7).split("\\.", -1).length == 3) {
            return Stream.of(Scheme.JWT);
        }
        return Stream.empty();
    }

    private static Stream<Scheme> fromDeclaration(SecurityScheme scheme) {
        if (scheme == null || scheme.getType() != SecurityScheme.Type.HTTP) {
            return Stream.empty();
        }
        if ("basic".equalsIgnoreCase(scheme.getScheme())) {
            return Stream.of(Scheme.BASIC);
        }
        return "bearer".equalsIgnoreCase(scheme.getScheme()) && "JWT".equalsIgnoreCase(scheme.getBearerFormat())
                ? Stream.of(Scheme.JWT) : Stream.empty();
    }

    private static List<SecurityRequirement> requirements(FuzzingData data) {
        Operation operation = data.getPathItem() == null || data.getMethod() == null ? null : HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        if (operation != null && operation.getSecurity() != null) {
            return operation.getSecurity();
        }
        return Optional.ofNullable(data.getOpenApi()).map(OpenAPI::getSecurity).orElse(List.of());
    }

    private static Map<String, SecurityScheme> securitySchemes(FuzzingData data) {
        return Optional.ofNullable(data.getOpenApi()).map(OpenAPI::getComponents)
                .map(io.swagger.v3.oas.models.Components::getSecuritySchemes).orElse(Map.of());
    }

    static List<String> values(Scheme scheme) {
        return scheme == Scheme.BASIC ? EncodedAuthenticationGenerator.basicValues() : EncodedAuthenticationGenerator.jwtValues();
    }
}
