package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.HeadersMutator;
import com.endava.cats.generator.Cloner;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsRandom;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Injects or overrides HTTP method-override, reverse-proxy forwarding, conditional precondition,
 * and byte-range headers to expose routing, caching, and header-parser bugs.
 */
@Singleton
public class RandomProtocolAndOverrideHeadersMutator implements HeadersMutator {
    private static final List<CatsHeader> PROTOCOL_HEADERS = List.of(
            CatsHeader.builder().name("X-HTTP-Method-Override").value("DELETE").build(),
            CatsHeader.builder().name("X-Method-Override").value("PUT").build(),
            CatsHeader.builder().name("X-Forwarded-For").value("127.0.0.1, ::1, 0.0.0.0").build(),
            CatsHeader.builder().name("X-Forwarded-Host").value("localhost:0").build(),
            CatsHeader.builder().name("Forwarded").value("for=\"[::1]:-1\";proto=http;by=127.0.0.1").build(),
            CatsHeader.builder().name("If-Match").value("\"*\"").build(),
            CatsHeader.builder().name("If-None-Match").value("W/\"invalid-etag\"").build(),
            CatsHeader.builder().name("If-Modified-Since").value("Invalid-Date").build(),
            CatsHeader.builder().name("Range").value("bytes=-1--1, 999999999999-0").build(),
            CatsHeader.builder().name("Max-Forwards").value("-1").build());

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        CatsHeader selected = PROTOCOL_HEADERS.get(CatsRandom.instance().nextInt(PROTOCOL_HEADERS.size()));
        clone.removeIf(existing -> existing.getName().equalsIgnoreCase(selected.getName()));
        clone.add(selected);
        return clone;
    }

    @Override
    public String description() {
        return "inject or override HTTP method-override, forwarding, precondition, and range headers";
    }
}
