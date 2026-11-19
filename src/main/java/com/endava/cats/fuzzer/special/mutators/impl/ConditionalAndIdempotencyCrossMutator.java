package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.CrossLayerMutator;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

/**
 * Pairs HTTP conditional precondition or {@code Idempotency-Key} headers with a format-preserving
 * mutation of the target body field to expose state/cache/idempotency conflict bugs.
 */
@Singleton
public class ConditionalAndIdempotencyCrossMutator implements CrossLayerMutator {
    private static final List<CatsHeader> PRECONDITION_HEADERS = List.of(
            CatsHeader.builder().name("If-Match").value("W/\"cats-etag\"").build(),
            CatsHeader.builder().name("If-None-Match").value("*").build(),
            CatsHeader.builder().name("If-Unmodified-Since").value("Thu, 01 Jan 1970 00:00:00 GMT").build(),
            CatsHeader.builder().name("Idempotency-Key").value("cats-idem-key").build());

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object current = JsonUtils.getVariableFromJson(inputJson, selectedField);
        Object mutated = FormatPreservingValueMutator.mutatePreservingShape(current);
        return CatsUtil.justReplaceField(inputJson, selectedField, mutated).json();
    }

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        CatsHeader selected = PRECONDITION_HEADERS.get(CatsRandom.instance().nextInt(PRECONDITION_HEADERS.size()));
        return CrossLayerMutator.replaceHeader(headers, selected.getName(), selected.getValue());
    }

    @Override
    public String description() {
        return "pair HTTP precondition and Idempotency-Key headers with format-preserving field mutations";
    }
}
