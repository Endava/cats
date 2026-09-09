package com.endava.cats.io;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.QueryParameterSerialization;
import com.endava.cats.model.RequestTarget;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Collection;
import java.util.HashSet;
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
    /** Field names used by response-error validation. */
    @Singular
    private final Set<String> responseValidationFields;
    @Singular
    private final Set<RequestTarget> mutationTargets;
    @Builder.Default
    private final Set<String> pathParams = new HashSet<>();
    @Builder.Default
    private final Set<String> queryParams = new HashSet<>();
    @Builder.Default
    private final Map<String, QueryParameterSerialization> queryParameterSerializations = Map.of();

    /**
     * Creates a service-call builder populated with the operation metadata shared by all executors.
     * Executors only need to override the request parts they deliberately change.
     *
     * @param data generated operation and payload data
     * @return a builder initialized with the common service-call data
     */
    public static ServiceDataBuilder from(FuzzingData data) {
        return builder()
                .relativePath(data.getPath())
                .contractPath(data.getContractPath())
                .headers(data.getHeaders())
                .payload(data.getPayload())
                .originalPayload(data.getPayload())
                .queryParams(data.getQueryParams())
                .queryParameterSerializations(data.getQueryParameterSerializations())
                .httpMethod(data.getMethod())
                .contentType(data.getFirstRequestContentType())
                .pathParamsPayload(data.getPathParamsPayload());
    }

    /**
     * Checks if the content type of the response is JSON.
     *
     * @return {@code true} if the content type matches the pattern "application/.*[+]?json;?.*", {@code false} otherwise.
     */
    public boolean isJsonContentType() {
        return this.contentType.toLowerCase(Locale.ROOT).matches("application/.*[+]?json;?.*");
    }

    /**
     * Returns the request parts explicitly marked as mutated by the executor.
     *
     * @return all mutation targets in deterministic order
     */
    public List<RequestTarget> getAllMutationTargets() {
        return mutationTargets.stream()
                .sorted(java.util.Comparator.comparing(target -> target.getLocation().name() + "#" + target.getName()))
                .toList();
    }

    /**
     * Checks whether a field at the supplied request location is deliberately mutated.
     * @param field field path or parameter name
     * @param location request location
     * @return true when correlation or reference data must preserve the mutation
     */
    public boolean isFuzzedField(String field, RequestTarget.Location location) {
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
        return mutationTargets.stream().anyMatch(target -> target.getLocation() == RequestTarget.Location.HEADER &&
                        target.getName().equalsIgnoreCase(headerName));
    }

    /**
     * Checks whether the request carries any declared mutation metadata.
     *
     * @return true when the request carries mutation metadata
     */
    public boolean hasDeclaredMutation() {
        return !mutationTargets.isEmpty();
    }
}
