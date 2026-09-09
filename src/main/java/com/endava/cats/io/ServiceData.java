package com.endava.cats.io;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.MutationTarget;
import com.endava.cats.model.QueryParameterSerialization;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DTO holding all data needed to do a call to a service.
 */
@Builder
@Getter
public class ServiceData {
    private final String contractPath;
    private final String relativePath;
    private final Collection<CatsHeader> headers;
    private final String payload;
    /** The generated payload before a fuzzer applied mutations. */
    private final String originalPayload;
    private final HttpMethod httpMethod;
    private final String contentType;
    private final String pathParamsPayload;
    @Builder.Default
    private final boolean replaceRefData = true;
    @Builder.Default
    private final boolean replaceUrlParams = true;
    @Builder.Default
    private final boolean validJson = true;
    /**
     * Set to true if headers supplied by the user will be added or false otherwise.
     * There are Fuzzers which needs this level of control.
     */
    @Builder.Default
    private final boolean addUserHeaders = true;
    /**
     * Any header that will get removed before sending the request to the service.
     */
    @Builder.Default
    private final Set<String> skippedHeaders = new HashSet<>();
    @Singular
    private final Set<String> fuzzedFields;
    @Singular
    private final Set<String> fuzzedHeaders;
    @Singular
    private final Set<MutationTarget> mutationTargets;
    @Builder.Default
    private final Set<String> pathParams = new HashSet<>();
    @Builder.Default
    private final Set<String> queryParams = new HashSet<>();
    @Builder.Default
    private final Map<String, QueryParameterSerialization> queryParameterSerializations = Map.of();

    /**
     * Checks if the content type of the response is JSON.
     *
     * @return {@code true} if the content type matches the pattern "application/.*[+]?json;?.*", {@code false} otherwise.
     */
    public boolean isJsonContentType() {
        return this.contentType.toLowerCase(Locale.ROOT).matches("application/.*[+]?json;?.*");
    }

    /**
     * Returns explicit mutation targets together with targets derived from the existing functional
     * fuzzed-field and fuzzed-header metadata.
     *
     * @return all mutation targets in deterministic order
     */
    public List<MutationTarget> getAllMutationTargets() {
        Set<MutationTarget> targets = new LinkedHashSet<>(mutationTargets);
        fuzzedFields.stream().map(this::classifyField).forEach(targets::add);
        fuzzedHeaders.stream().map(MutationTarget::header).forEach(targets::add);
        return targets.stream()
                .sorted(java.util.Comparator.comparing(target -> target.getLocation().name() + "#" + target.getName()))
                .toList();
    }

    /**
     * Checks whether a field at the supplied request location is deliberately mutated.
     * Legacy fuzzed fields are included for compatibility with iterator executors.
     *
     * @param field field path or parameter name
     * @param location request location
     * @return true when correlation or reference data must preserve the mutation
     */
    public boolean isFuzzedField(String field, MutationTarget.Location location) {
        if (fuzzedFields.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(field))) {
            return true;
        }
        return mutationTargets.stream()
                .anyMatch(target -> target.getLocation() == location && target.getName().equalsIgnoreCase(field));
    }

    /**
     * Checks whether a header is deliberately mutated.
     *
     * @param headerName header name
     * @return true when supplied header data must preserve the mutation
     */
    public boolean isFuzzedHeader(String headerName) {
        return fuzzedHeaders.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(headerName)) ||
                mutationTargets.stream().anyMatch(target -> target.getLocation() == MutationTarget.Location.HEADER &&
                        target.getName().equalsIgnoreCase(headerName));
    }

    /**
     * Checks whether the request carries any declared mutation metadata.
     *
     * @return true when the request carries any legacy or typed mutation metadata
     */
    public boolean hasDeclaredMutation() {
        return !fuzzedFields.isEmpty() || !fuzzedHeaders.isEmpty() || !mutationTargets.isEmpty();
    }

    private MutationTarget classifyField(String field) {
        String rootField = field.substring(0, field.indexOf('#') < 0 ? field.length() : field.indexOf('#'));
        if (queryParams.contains(field) || queryParams.contains(rootField)) {
            return MutationTarget.query(field);
        }
        String simpleField = field.substring(field.lastIndexOf('#') + 1);
        if (relativePath != null && relativePath.contains("{" + simpleField + "}")) {
            return MutationTarget.path(field);
        }
        return MutationTarget.body(field);
    }
}
