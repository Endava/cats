package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.Cloner;
import com.endava.cats.model.CatsHeader;
import com.google.common.net.HttpHeaders;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Collection;
import java.util.Set;

/**
 * Sends dummy values in the transfer encoding header
 */
@Singleton
public class RandomTransferEncodingHeaderMutator implements Mutator {
    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        clone.add(CatsHeader.builder()
                .name(HttpHeaders.TRANSFER_ENCODING)
                .value(RandomStringUtils.secure().next(10))
                .build());
        return clone;
    }

    @Override
    public String description() {
        return "replace the transfer encoding header with random values";
    }
}
