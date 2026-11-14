package com.endava.cats.report.error.fields;

import java.util.Optional;

/**
 * Tells which {@link ErrorFieldRole} a field of a JSON error body plays, from its name.
 */
@FunctionalInterface
public interface ErrorFieldRoles {

    /**
     * Resolves the role of a field.
     *
     * @param fieldName the JSON key
     * @return the role, or empty when the field has none
     */
    Optional<ErrorFieldRole> roleOf(String fieldName);

    /**
     * Tells whether a field plays the given role.
     *
     * @param fieldName the JSON key
     * @param role      the role to test
     * @return {@code true} if {@link #roleOf(String)} is the given role
     */
    default boolean has(String fieldName, ErrorFieldRole role) {
        return roleOf(fieldName).filter(role::equals).isPresent();
    }
}
