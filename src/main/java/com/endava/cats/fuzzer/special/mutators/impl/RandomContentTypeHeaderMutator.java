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
 * Send random values in the content type header.
 */
@Singleton
public class RandomContentTypeHeaderMutator implements Mutator {

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        String randomValue = StringGenerator.getUnsupportedMediaTypes()
                .get(CatsUtil.random().nextInt(StringGenerator.getUnsupportedMediaTypes().size()));

        clone.add(CatsHeader.builder()
                .name(HttpHeaders.CONTENT_TYPE)
                .value(randomValue)
                .build());
        return clone;
    }

    @Override
    public String description() {
        return "replace the content type header with random unsupported media types ";
    }
}
