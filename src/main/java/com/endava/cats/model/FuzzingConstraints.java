package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FuzzingConstraints {
    private final boolean hasMinlength;
    private final boolean hasRequiredFieldsFuzzed;


    public boolean hasMinLengthOrMandatoryFieldsFuzzed() {
        return hasMinlength || hasRequiredFieldsFuzzed;
    }

    public String getRequiredString() {
        return (String.valueOf(hasRequiredFieldsFuzzed).toUpperCase()) + (hasMinlength && !hasRequiredFieldsFuzzed ? " but has minLength" : "");
    }


}
