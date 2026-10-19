package com.endava.cats.pool;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.google.gson.JsonElement;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records bounded successful exchanges for second-phase fuzzers and tracks per-operation unresolved-parameter diagnostics.
 */
final class SuccessfulExchangeRecorder {
    private static final int MAX_OPERATION_DIAGNOSTICS = 2_000;
    private static final int MAX_EXCHANGES = 256;
    private static final int MAX_EXCHANGES_PER_OPERATION = 4;
    private static final int MAX_EXCHANGE_BODY_LENGTH = 32_768;
    private static final int MAX_EXCHANGE_HEADER_LENGTH = 2_048;

    private final Map<OperationKey, OperationDiagnostics> operationDiagnostics = new LinkedHashMap<>();
    private final ArrayDeque<RuntimeResourcePool.SuccessfulExchange> successfulExchanges = new ArrayDeque<>();

    void clear() {
        operationDiagnostics.clear();
        successfulExchanges.clear();
    }

    boolean hasSucceededWithUnmodifiedRequest(String path, HttpMethod method) {
        OperationDiagnostics diagnostics = operationDiagnostics.get(new OperationKey(path, method));
        return diagnostics != null && diagnostics.successes > 0;
    }

    List<RuntimeResourcePool.SuccessfulExchange> successfulExchanges(String path, HttpMethod method) {
        return successfulExchanges.stream()
                .filter(exchange -> exchange.path().equals(path) && exchange.method() == method)
                .toList();
    }

    void recordSuccessfulExchange(ServiceData data, CatsRequest request, CatsResponse response) {
        if (!isEligibleExchange(data, request, response)) {
            return;
        }
        OperationKey key = new OperationKey(data.getRelativePath(), data.getHttpMethod());
        RuntimeResourcePool.SuccessfulExchange exchange = new RuntimeResourcePool.SuccessfulExchange(
                key.path(), key.method(), request.getUrl(),
                request.getPayload(), response.getResponseCode(), response.getBody(),
                boundedHeader(response, "Location"), boundedHeader(response, "ETag"));
        evictOldestExchangeForOperationIfFull(key);
        successfulExchanges.addLast(exchange);
        if (successfulExchanges.size() > MAX_EXCHANGES) {
            successfulExchanges.removeFirst();
        }
    }

    void recordOperationDiagnostics(ServiceData data, CatsRequest request, CatsResponse response,
                                    RuntimeResourcePool.ResolvedRequest resolvedRequest, String effectivePayload,
                                    Function<ServiceData, List<TargetField>> diagnosticTargetsSupplier) {
        if (!isUnmodifiedContractRequest(data)) {
            return;
        }
        OperationKey key = new OperationKey(data.getRelativePath(), data.getHttpMethod());
        OperationDiagnostics diagnostics = operationDiagnostics.get(key);
        if (diagnostics == null) {
            if (operationDiagnostics.size() >= MAX_OPERATION_DIAGNOSTICS) {
                return;
            }
            diagnostics = new OperationDiagnostics();
            operationDiagnostics.put(key, diagnostics);
        }
        int responseCode = response.getResponseCode();
        diagnostics.register(responseCode);
        Set<RequestTarget> correlatedTargets = Optional.ofNullable(resolvedRequest)
                .map(RuntimeResourcePool.ResolvedRequest::correlations)
                .orElse(List.of()).stream().map(ResourceCorrelation::getTarget).collect(Collectors.toSet());
        Map<String, StoredValue> actualPathValues = ResourceValueExtractor.extractPathValues(data.getRelativePath(), request.getUrl());
        for (TargetField target : diagnosticTargetsSupplier.apply(data)) {
            RequestTarget requestTarget = new RequestTarget(target.location(), target.path());
            Optional<Boolean> resolved = correlatedTargets.contains(requestTarget) ? Optional.of(true) :
                    valueReplaced(data, effectivePayload, target, actualPathValues);
            OperationDiagnostics current = diagnostics;
            resolved.ifPresent(value -> current.recordParameter(requestTarget, value, responseCode));
        }
    }

    List<RuntimeResourcePool.UnresolvedOperation> unresolvedOperations() {
        List<RuntimeResourcePool.UnresolvedOperation> result = new ArrayList<>();
        operationDiagnostics.forEach((key, diagnostics) -> {
            if (diagnostics.successes > 0 || diagnostics.onlyAuthFailures()) {
                return;
            }
            List<RuntimeResourcePool.UnresolvedParameter> parameters = new ArrayList<>();
            diagnostics.parameters.forEach((target, stats) -> stats.reason().ifPresent(reason ->
                    parameters.add(new RuntimeResourcePool.UnresolvedParameter(target, reason, producerOutcome(key.path(), target)))));
            if (!parameters.isEmpty()) {
                result.add(new RuntimeResourcePool.UnresolvedOperation(diagnostics.outcome(key), List.copyOf(parameters)));
            }
        });
        return List.copyOf(result);
    }

    List<TargetField> buildDiagnosticTargets(ServiceData data,
                                             BiPredicate<ServiceData, TargetField> excludedTargetPredicate,
                                             Function<Boolean, List<TargetField>> reusableTargetsProvider) {
        Map<RequestTarget, TargetField> targets = new LinkedHashMap<>();
        List<TargetField> candidates = new ArrayList<>();
        if (JsonUtils.isValidJson(data.getPayload())) {
            candidates.addAll(reusableTargetsProvider.apply(false));
        }
        if (JsonUtils.isValidJson(data.getPathParamsPayload())) {
            candidates.addAll(reusableTargetsProvider.apply(true));
        }
        candidates.stream()
                .filter(target -> target.location() == RequestTarget.Location.PATH ||
                        ResourceSemanticNaming.isIdentifierName(target.name()))
                .filter(target -> !excludedTargetPredicate.test(data, target))
                .forEach(target -> targets.putIfAbsent(new RequestTarget(target.location(), target.path()), target));
        return List.copyOf(targets.values());
    }

    private RuntimeResourcePool.OperationOutcome producerOutcome(String path, RequestTarget target) {
        if (target.location() == RequestTarget.Location.PATH) {
            List<String> segments = ResourceSemanticNaming.pathSegments(path);
            int index = segments.indexOf("{" + target.name() + "}");
            if (index > 0) {
                OperationKey producerKey = new OperationKey("/" + String.join("/", segments.subList(0, index)), HttpMethod.POST);
                OperationDiagnostics directProducer = operationDiagnostics.get(producerKey);
                if (directProducer != null) {
                    return directProducer.outcome(producerKey);
                }
            }
        }
        String leafName = target.name().substring(target.name().lastIndexOf('#') + 1);
        Set<String> qualifiedAliases = ResourceSemanticNaming.semanticAliases(target.name(), leafName).stream()
                .filter(alias -> !ResourceSemanticNaming.SIMPLE_IDENTIFIERS.contains(alias))
                .collect(Collectors.toSet());
        if (qualifiedAliases.isEmpty()) {
            return null;
        }
        return operationDiagnostics.entrySet().stream()
                .filter(entry -> entry.getKey().method() == HttpMethod.POST && !entry.getKey().path().equals(path))
                .filter(entry -> ResourceSemanticNaming.isCollectionPath(entry.getKey().path()))
                .filter(entry -> ResourceSemanticNaming.primaryResourceIdentifierAliases(entry.getKey().path()).stream()
                        .anyMatch(qualifiedAliases::contains))
                .findFirst()
                .map(entry -> entry.getValue().outcome(entry.getKey()))
                .orElse(null);
    }

    private Optional<Boolean> valueReplaced(ServiceData data, String effectivePayload,
                                            TargetField target, Map<String, StoredValue> actualPathValues) {
        if (target.location() == RequestTarget.Location.PATH) {
            StoredValue actual = actualPathValues.get(target.name());
            Object generated = generatedPathValue(data, target.name());
            if (actual == null || generated == null) {
                return Optional.empty();
            }
            return Optional.of(!ResourceSemanticNaming.sameValue(generated, actual.value()));
        }
        if (!JsonUtils.isValidJson(effectivePayload)) {
            return Optional.empty();
        }
        List<JsonElement> actual = ResourceValueExtractor.valuesAtPath(effectivePayload, target.path());
        if (actual.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(!actual.equals(ResourceValueExtractor.valuesAtPath(data.getPayload(), target.path())));
    }

    private Object generatedPathValue(ServiceData data, String name) {
        for (String payload : List.of(Optional.ofNullable(data.getPathParamsPayload()).orElse(""),
                Optional.ofNullable(data.getPayload()).orElse(""))) {
            if (JsonUtils.isValidJson(payload)) {
                Object value = JsonUtils.getVariableFromJson(payload, name);
                if (!JsonUtils.isNotSet(String.valueOf(value))) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean isEligibleExchange(ServiceData data, CatsRequest request, CatsResponse response) {
        return isUnmodifiedContractRequest(data) && !response.isBodyTruncated() &&
                Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).contains(data.getHttpMethod()) &&
                response.getResponseCode() != 202 && StringUtils.isNotBlank(request.getUrl()) &&
                request.getUrl().length() <= MAX_EXCHANGE_HEADER_LENGTH &&
                !tooLong(request.getPayload()) && !tooLong(response.getBody());
    }

    private boolean isUnmodifiedContractRequest(ServiceData data) {
        boolean contractPath = StringUtils.isBlank(data.getContractPath()) ||
                data.getContractPath().equals(data.getRelativePath());
        return contractPath && data.getRelativePath() != null && data.getHttpMethod() != null &&
                data.isReplaceRefData() && data.isReplaceUrlParams() && data.isAddUserHeaders() &&
                Optional.ofNullable(data.getSkippedHeaders()).orElse(Set.of()).isEmpty() &&
                !ResourceValueExtractor.isFuzzedRequest(data);
    }

    private void evictOldestExchangeForOperationIfFull(OperationKey key) {
        List<RuntimeResourcePool.SuccessfulExchange> existing = successfulExchanges(key.path(), key.method());
        if (existing.size() >= MAX_EXCHANGES_PER_OPERATION) {
            successfulExchanges.remove(existing.getFirst());
        }
    }

    private boolean tooLong(String value) {
        return value != null && value.length() > MAX_EXCHANGE_BODY_LENGTH;
    }

    private String boundedHeader(CatsResponse response, String name) {
        KeyValuePair<String, String> header = response.getHeader(name);
        return header == null || header.getValue() == null || header.getValue().length() > MAX_EXCHANGE_HEADER_LENGTH
                ? null : header.getValue();
    }

    private record OperationKey(String path, HttpMethod method) {
    }

    private static final class ParameterDiagnostics {
        private int resolved;
        private int unresolved;
        private int resolvedNotFound;

        Optional<RuntimeResourcePool.UnresolvedReason> reason() {
            if (resolved == 0 && unresolved > 0) {
                return Optional.of(RuntimeResourcePool.UnresolvedReason.NO_VALUE_CAPTURED);
            }
            if (resolved > 0 && resolvedNotFound == resolved) {
                return Optional.of(RuntimeResourcePool.UnresolvedReason.CAPTURED_VALUES_NOT_FOUND);
            }
            return Optional.empty();
        }
    }

    private static final class OperationDiagnostics {
        private final Map<Integer, Integer> statusCodes = new LinkedHashMap<>();
        private final Map<RequestTarget, ParameterDiagnostics> parameters = new LinkedHashMap<>();
        private int requests;
        private int successes;

        void register(int responseCode) {
            requests++;
            if (ResponseCodeFamily.is2xxCode(responseCode)) {
                successes++;
            }
            statusCodes.merge(responseCode, 1, Integer::sum);
        }

        void recordParameter(RequestTarget target, boolean resolved, int responseCode) {
            ParameterDiagnostics stats = parameters.computeIfAbsent(target, _ -> new ParameterDiagnostics());
            if (!resolved) {
                stats.unresolved++;
                return;
            }
            stats.resolved++;
            if (responseCode == 404 || responseCode == 410) {
                stats.resolvedNotFound++;
            }
        }

        boolean onlyAuthFailures() {
            return statusCodes.keySet().stream().allMatch(code -> code == 401 || code == 403);
        }

        RuntimeResourcePool.OperationOutcome outcome(OperationKey key) {
            return new RuntimeResourcePool.OperationOutcome(key.path(), key.method(), requests, successes, statusCodes);
        }
    }
}
