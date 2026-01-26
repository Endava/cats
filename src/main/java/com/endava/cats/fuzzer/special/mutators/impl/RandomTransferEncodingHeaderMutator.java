package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.HeadersMutator;
import com.endava.cats.generator.Cloner;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.HttpHeaders;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Set;

/**
 * Sends dummy values in the transfer encoding header
 */
@Singleton
public class RandomTransferEncodingHeaderMutator implements HeadersMutator {
    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        clone.add(CatsHeader.builder()
                .name(HttpHeaders.TRANSFER_ENCODING)
                .value(CatsRandom.next(10))
                .build());
        return clone;
    }

    @Override
    public String description() {
        return "replace the transfer encoding header with random values";
    }
}
