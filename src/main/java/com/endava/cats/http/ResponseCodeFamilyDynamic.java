package com.endava.cats.http;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Class used to create dynamic http response code sets.
 */
public class ResponseCodeFamilyDynamic implements ResponseCodeFamily {
    private final List<String> responseCodes;

    public ResponseCodeFamilyDynamic(List<String> responseCodes) {
        this.responseCodes = List.copyOf(responseCodes);
    }

    @Override
    public String asString() {
        return String.join("|", Optional.ofNullable(responseCodes).orElse(Collections.emptyList()));
    }

    @Override
    public List<String> allowedResponseCodes() {
        return Optional.ofNullable(responseCodes).orElse(Collections.emptyList());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ResponseCodeFamilyDynamic that) {
            return Objects.equals(responseCodes, that.responseCodes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(responseCodes);
    }
}
