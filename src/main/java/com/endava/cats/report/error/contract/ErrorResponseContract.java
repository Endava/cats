package com.endava.cats.report.error.contract;

import com.endava.cats.report.error.fields.ErrorFieldRole;
import com.endava.cats.report.error.fields.ErrorFieldRoleResolver;
import com.endava.cats.report.error.fields.ErrorFieldRoles;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the OpenAPI contract declares about the error body of one operation and status code: the roles of the
 * declared properties, taken from their names, and the properties that vary per occurrence.
 *
 * @param topLevelFields the properties declared at the top level of the error schema
 * @param requiredFields the top-level properties the schema requires
 * @param roles          the role of each declared property, at any depth; properties without role are not listed
 */
public record ErrorResponseContract(Set<String> topLevelFields, Set<String> requiredFields, Map<String, ErrorFieldRole> roles)
        implements ErrorFieldRoles {
    public static final ErrorResponseContract NONE = new ErrorResponseContract(Set.of(), Set.of(), Map.of());

    @Override
    public Optional<ErrorFieldRole> roleOf(String fieldName) {
        return Optional.ofNullable(roles.get(fieldName));
    }

    /**
     * A body matches the declared schema when it has every required property and at least half of its own top-level
     * fields are declared. Containers, proxies and unhandled exceptions often answer with something else than the
     * documented error body.
     *
     * @param body the parsed error body
     * @return {@code true} if the declared roles describe the body
     */
    boolean matches(JsonObject body) {
        long declared = body.keySet().stream().filter(topLevelFields::contains).count();
        return declared > 0 && body.keySet().containsAll(requiredFields) && 2 * declared >= body.size();
    }

    /**
     * Chooses the roles to read a body with: the declared ones when the body matches the contract, the ones guessed
     * from the field names otherwise.
     *
     * @param body the parsed error body
     * @return the field roles for the body
     */
    public ErrorFieldRoles rolesFor(JsonObject body) {
        return matches(body) ? this : ErrorFieldRoleResolver.BY_NAME;
    }
}
