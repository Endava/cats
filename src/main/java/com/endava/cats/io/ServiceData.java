package com.endava.cats.io;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.PayloadFormat;
import com.endava.cats.model.QueryParameterSerialization;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.ContentTypeUtils;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    /**
     * Explicit builder type kept visible to Javadoc and other source-level API tools.
     * Lombok augments this class with the generated builder fields and methods.
     */
    public static class ServiceDataBuilder {
        private ServiceDataBuilder() {
        }
    }

    private final String contractPath;
    private final String relativePath;
    private final Collection<CatsHeader> headers;
    private final String payload;
    /** The generated payload before a fuzzer applied mutations. */
    private final String originalPayload;
    private final HttpMethod httpMethod;
    private final String contentType;
    private final String acceptHeader;
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
    @Builder.Default
    private final Map<String, Set<String>> enumValuesByField = Map.of();
    @Builder.Default
    private final Set<String> discriminatorFields = Set.of();
    private final Schema<?> reqSchema;
    @Builder.Default
    private final Map<String, Schema> schemaMap = Map.of();
    /** Variables supplied by a custom fuzzer for resolving dynamic request values. */
    @Builder.Default
    private final Map<String, String> dynamicVariables = Map.of();

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
                .enumValuesByField(extractEnumValuesByField(data))
                .discriminatorFields(extractDiscriminatorFields(data))
                .reqSchema(data.getReqSchema())
                .schemaMap(data.getSchemaMap() == null ? Map.of() : data.getSchemaMap())
                .httpMethod(data.getMethod())
                .contentType(data.getFirstRequestContentType())
                .acceptHeader(resolveAcceptHeader(data))
                .pathParamsPayload(data.getPathParamsPayload());
    }

    private static Map<String, Set<String>> extractEnumValuesByField(FuzzingData data) {
        if (data.getRequestPropertyTypes() == null || data.getRequestPropertyTypes().isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        data.getRequestPropertyTypes().forEach((field, schema) -> {
            if (field != null && schema != null && schema.getEnum() != null && !schema.getEnum().isEmpty()) {
                Set<String> allowed = new LinkedHashSet<>();
                for (Object item : schema.getEnum()) {
                    if (item != null) {
                        allowed.add(String.valueOf(item));
                    }
                }
                if (!allowed.isEmpty()) {
                    result.put(field, Set.copyOf(allowed));
                }
            }
        });
        return Map.copyOf(result);
    }

    public boolean isAllowedEnumValue(String fieldPath, Object candidateValue) {
        if (enumValuesByField == null || enumValuesByField.isEmpty() || candidateValue == null) {
            return true;
        }
        Set<String> allowed = enumValuesByField.get(fieldPath);
        return allowed == null || allowed.isEmpty() || allowed.contains(String.valueOf(candidateValue));
    }

    public boolean isDiscriminatorOrConstantField(String fieldPath, String fieldName) {
        if (enumValuesByField != null) {
            Set<String> allowed = enumValuesByField.get(fieldPath);
            if (allowed != null && allowed.size() == 1) {
                return true;
            }
        }
        if (discriminatorFields == null || discriminatorFields.isEmpty()) {
            return false;
        }
        return (fieldPath != null && discriminatorFields.contains(fieldPath.toLowerCase(Locale.ROOT)))
                || (fieldName != null && discriminatorFields.contains(fieldName.toLowerCase(Locale.ROOT)));
    }

    private static Set<String> extractDiscriminatorFields(FuzzingData data) {
        Set<String> discriminators = new LinkedHashSet<>();
        addDiscriminatorFromSchema(data.getReqSchema(), discriminators);
        if (data.getRequestPropertyTypes() != null) {
            data.getRequestPropertyTypes().values().forEach(schema -> addDiscriminatorFromSchema(schema, discriminators));
        }
        if (data.getSchemaMap() != null) {
            data.getSchemaMap().values().forEach(schema -> addDiscriminatorFromSchema(schema, discriminators));
        }
        return discriminators.isEmpty() ? Set.of() : Set.copyOf(discriminators);
    }

    private static void addDiscriminatorFromSchema(Schema<?> schema, Set<String> discriminators) {
        if (schema != null && schema.getDiscriminator() != null
                && schema.getDiscriminator().getPropertyName() != null
                && !schema.getDiscriminator().getPropertyName().isBlank()) {
            discriminators.add(schema.getDiscriminator().getPropertyName().trim().toLowerCase(Locale.ROOT));
        }
    }

    private static String resolveAcceptHeader(FuzzingData data) {
        return ContentTypeUtils.resolveAcceptHeader(data.getResponseContentTypes());
    }

    public PayloadFormat getPayloadFormat() {
        return PayloadFormat.from(contentType);
    }

    /**
     * Returns the request parts explicitly marked as mutated by the executor.
     *
     * @return all mutation targets in deterministic order
     */
    public List<RequestTarget> getAllMutationTargets() {
        return mutationTargets.stream()
                .sorted(RequestTarget.ordering())
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
                .anyMatch(target -> target.location() == location && target.name().equalsIgnoreCase(field));
    }

    /**
     * Checks whether a header is deliberately mutated.
     *
     * @param headerName header name
     * @return true when supplied header data must preserve the mutation
     */
    public boolean isFuzzedHeader(String headerName) {
        return mutationTargets.stream().anyMatch(target -> target.location() == RequestTarget.Location.HEADER &&
                        target.name().equalsIgnoreCase(headerName));
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
