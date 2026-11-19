package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.generator.Cloner;
import com.endava.cats.model.CatsHeader;

import java.util.Collection;
import java.util.Set;

/**
 * Marker interface for mutators that coordinate a request-body field mutation
 * with a semantically paired HTTP header mutation in the same request.
 */
public interface CrossLayerMutator extends BodyMutator, HeadersMutator {

    @Override
    String mutate(String inputJson, String selectedField);

    @Override
    Collection<CatsHeader> mutate(Collection<CatsHeader> headers);

    /**
     * Clones {@code headers} and replaces (or adds) the header matching {@code headerName}.
     *
     * @param headers    the source headers
     * @param headerName the target header name
     * @param value      the header value to set
     * @return a mutable set of cloned headers containing the updated header
     */
    static Set<CatsHeader> replaceHeader(Collection<CatsHeader> headers, String headerName, String value) {
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        clone.removeIf(existing -> existing.getName().equalsIgnoreCase(headerName));
        clone.add(CatsHeader.builder().name(headerName).value(value).build());
        return clone;
    }
}
