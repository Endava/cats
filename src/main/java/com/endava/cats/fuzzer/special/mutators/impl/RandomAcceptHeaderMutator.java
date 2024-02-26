package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.Cloner;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsUtil;
import com.google.common.net.HttpHeaders;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Set;

/**
 * Send random values in the accept header.
 */
@Singleton
public class RandomAcceptHeaderMutator implements Mutator {

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        String randomValue = StringGenerator.getUnsupportedMediaTypes()
                .get(CatsUtil.random().nextInt(StringGenerator.getUnsupportedMediaTypes().size()));

        clone.add(CatsHeader.builder()
                .name(HttpHeaders.ACCEPT)
                .value(randomValue)
                .build());
        return clone;
    }

    @Override
    public String description() {
        return "replace the accept header with random unsupported media types ";
    }
}
