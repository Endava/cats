package com.endava.cats.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Traceability metadata describing how a request was mutated and enriched before execution.
 */
public final class RequestProvenance {
    private final List<RequestTarget> mutationTargets = new ArrayList<>();
    private final List<ResourceCorrelation> runtimeCorrelations = new ArrayList<>();

    public void addMutationTargets(Collection<RequestTarget> targets) {
        targets.stream().filter(target -> !mutationTargets.contains(target)).forEach(mutationTargets::add);
    }

    public void addRuntimeCorrelation(ResourceCorrelation correlation) {
        runtimeCorrelations.add(correlation);
    }

    /**
     * Returns the mutation targets captured so far.
     *
     * @return immutable snapshot of mutation targets
     */
    public List<RequestTarget> getMutationTargets() {
        return List.copyOf(mutationTargets);
    }

    /**
     * Returns the runtime correlations captured so far.
     *
     * @return immutable snapshot of runtime correlations
     */
    public List<ResourceCorrelation> getRuntimeCorrelations() {
        return List.copyOf(runtimeCorrelations);
    }

    public boolean hasMutationTargets() {
        return !mutationTargets.isEmpty();
    }

    public boolean hasRuntimeCorrelations() {
        return !runtimeCorrelations.isEmpty();
    }
}
