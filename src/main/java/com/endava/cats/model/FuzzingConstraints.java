package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Locale;

/**
 * Represents constraints related to fuzzing, including information about the presence of a minimum length
 * and whether required fields have been fuzzed.
 */
@Builder
@Getter
public class FuzzingConstraints {
    private final boolean hasMinlength;
    private final boolean hasRequiredFieldsFuzzed;

    /**
     * Checks if either a minimum length or mandatory fields have been fuzzed.
     *
     * @return {@code true} if there is a minimum length or mandatory fields have been fuzzed, {@code false} otherwise.
     */
    public boolean hasMinLengthOrMandatoryFieldsFuzzed() {
        return hasMinlength || hasRequiredFieldsFuzzed;
    }

    /**
     * Gets a string representation of the required constraints.
     *
     * @return A string indicating whether required fields have been fuzzed and, if not, whether there is a minimum length.
     */
    public String getRequiredString() {
        return String.valueOf(hasRequiredFieldsFuzzed).toUpperCase(Locale.ROOT) + (hasMinlength && !hasRequiredFieldsFuzzed ? " but has minLength" : "");
    }
}
