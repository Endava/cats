package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.Cloner;
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
        String randomValue = BaseSecurityChecksHeadersFuzzer.UNSUPPORTED_MEDIA_TYPES
                .get(CatsUtil.random().nextInt(BaseSecurityChecksHeadersFuzzer.UNSUPPORTED_MEDIA_TYPES.size()));

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
