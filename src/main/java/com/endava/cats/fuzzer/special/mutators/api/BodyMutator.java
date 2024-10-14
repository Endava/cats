package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.model.CatsHeader;

import java.util.Collection;

/**
 * Marker interface for mutators that mutate the request body.
 */
public interface BodyMutator extends Mutator {


    @Override
    default Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        return headers;
    }
}
