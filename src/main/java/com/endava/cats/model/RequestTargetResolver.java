package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Resolves mutation targets only where the caller cannot declare a request location directly. */
public final class RequestTargetResolver {
    private RequestTargetResolver() {
        // utility class
    }

    /**
     * Resolves the HTTP locations represented by the generated payload carrier.
     * Body methods use it as the request body; other methods use it for path and query parameters.
     *
     * @param data operation data
     * @param field mutated field path
     * @return every request location affected by mutating the generated payload
     */
    public static List<RequestTarget> resolvePayloadField(FuzzingData data, String field) {
        return resolvePayloadFields(data, List.of(field));
    }

    /**
     * Resolves all fields changed in the generated payload carrier.
     *
     * @param data operation data
     * @param fields mutated field paths
     * @return every request location affected by mutating the generated payload
     */
    public static List<RequestTarget> resolvePayloadFields(FuzzingData data, Collection<String> fields) {
        if (HttpMethod.requiresBody(data.getMethod())) {
            return fields.stream()
                    .map(RequestTarget::body)
                    .distinct()
                    .sorted(RequestTarget.ordering())
                    .toList();
        }

        Set<RequestTarget> targets = new LinkedHashSet<>();
        for (String field : fields) {
            Set<RequestTarget> fieldTargets = parameterTargets(data, field);
            if (fieldTargets.isEmpty()) {
                fieldTargets.add(RequestTarget.query(field));
            }
            targets.addAll(fieldTargets);
        }
        return ordered(targets);
    }

    /**
     * Resolves a template mutation by comparing the generated request with the final request.
     * This also recognizes literal path placeholders such as {@code /users/FUZZ}.
     *
     * @param data original request data
     * @param mutatedRequest final request produced by the template fuzzer
     * @param field requested mutation target
     * @return request locations that actually changed
     */
    public static List<RequestTarget> resolveTemplateMutation(FuzzingData data, CatsRequest mutatedRequest,
                                                               String field) {
        RequestState original = RequestState.from(data);
        RequestState mutated = RequestState.from(data, mutatedRequest, original.parameterPayload());
        List<RequestTarget> targets = resolveChanges(data, original, mutated, List.of(field));
        return targets.isEmpty() ? resolveDeclaredTargets(data, field) : targets;
    }

    /**
     * Resolves custom-fuzzer mutations by comparing generated and customized request carriers.
     *
     * @param data original request data
     * @param mutatedPayload customized request payload
     * @param mutatedParameterPayload customized path/query parameter payload
     * @param mutatedHeaders customized headers
     * @param fields custom field names that may have changed request data
     * @return request locations that actually changed
     */
    public static List<RequestTarget> resolveCustomMutations(FuzzingData data, String mutatedPayload,
                                                              String mutatedParameterPayload,
                                                              Collection<CatsHeader> mutatedHeaders,
                                                              Collection<String> fields) {
        RequestState original = RequestState.from(data);
        RequestState mutated = RequestState.from(data, mutatedPayload, mutatedParameterPayload, mutatedHeaders);
        return resolveChanges(data, original, mutated, fields);
    }

    private static List<RequestTarget> resolveChanges(FuzzingData data, RequestState original,
                                                       RequestState mutated, Collection<String> fields) {
        Set<RequestTarget> targets = new LinkedHashSet<>();
        for (String field : fields) {
            if (jsonFieldChanged(original.bodyPayload(), mutated.bodyPayload(), field)) {
                targets.add(RequestTarget.body(field));
            }
            if (jsonFieldChanged(original.parameterPayload(), mutated.parameterPayload(), field)) {
                targets.addAll(parameterTargets(data, field));
            }
            if (headerChanged(original.headers(), mutated.headers(), field)) {
                targets.add(RequestTarget.header(field));
            }
            if (urlPartChanged(original.path(), mutated.path(), false) &&
                    isTargetPresentInUrlPart(original.path(), field, false)) {
                targets.add(RequestTarget.path(field));
            }
            if (urlPartChanged(original.path(), mutated.path(), true) &&
                    (isTargetPresentInUrlPart(original.path(), field, true) || isQueryParameter(data, field))) {
                targets.add(RequestTarget.query(field));
            }
        }
        return ordered(targets);
    }

    private static List<RequestTarget> resolveDeclaredTargets(FuzzingData data, String field) {
        Set<RequestTarget> targets = new LinkedHashSet<>(parameterTargets(data, field));
        if (hasHeader(data.getHeaders(), field)) {
            targets.add(RequestTarget.header(field));
        }
        if (HttpMethod.requiresBody(data.getMethod()) && JsonUtils.isFieldInJson(data.getPayload(), field)) {
            targets.add(RequestTarget.body(field));
        }
        if (targets.isEmpty()) {
            targets.add(HttpMethod.requiresBody(data.getMethod())
                    ? RequestTarget.body(field)
                    : RequestTarget.query(field));
        }
        return ordered(targets);
    }

    private static Set<RequestTarget> parameterTargets(FuzzingData data, String field) {
        Set<RequestTarget> targets = new LinkedHashSet<>();
        if (isPathParameter(data.getPath(), field)) {
            targets.add(RequestTarget.path(field));
        }
        if (isQueryParameter(data, field)) {
            targets.add(RequestTarget.query(field));
        }
        return targets;
    }

    private static boolean isPathParameter(String requestPath, String field) {
        if (requestPath == null) {
            return false;
        }
        String simpleName = simpleName(field);
        return requestPath.toLowerCase(Locale.ROOT).contains("{" + simpleName.toLowerCase(Locale.ROOT) + "}");
    }

    private static boolean isQueryParameter(FuzzingData data, String field) {
        String rootName = rootName(field);
        boolean declared = Objects.requireNonNullElse(data.getQueryParams(), Set.<String>of()).stream()
                .anyMatch(parameter -> parameter.equalsIgnoreCase(field) || parameter.equalsIgnoreCase(rootName));
        return declared || isQueryParameter(data.getPath(), field);
    }

    private static boolean isQueryParameter(String requestPath, String field) {
        String query = urlPart(requestPath, true);
        if (query == null) {
            return false;
        }
        String rootName = rootName(field);
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2)[0])
                .anyMatch(parameter -> parameter.equalsIgnoreCase(field) || parameter.equalsIgnoreCase(rootName));
    }

    private static boolean jsonFieldChanged(String original, String mutated, String field) {
        if (Objects.equals(original, mutated)) {
            return false;
        }
        Object originalValue = JsonUtils.getVariableFromJson(original, field);
        Object mutatedValue = JsonUtils.getVariableFromJson(mutated, field);
        return !Objects.deepEquals(originalValue, mutatedValue);
    }

    private static boolean headerChanged(Map<String, List<String>> original,
                                         Map<String, List<String>> mutated, String field) {
        return !Objects.equals(original.get(field), mutated.get(field));
    }

    private static boolean hasHeader(Collection<CatsHeader> headers, String field) {
        return Objects.requireNonNullElse(headers, List.<CatsHeader>of()).stream()
                .anyMatch(header -> header.getName().equalsIgnoreCase(field));
    }

    private static boolean urlPartChanged(String original, String mutated, boolean query) {
        return !Objects.equals(urlPart(original, query), urlPart(mutated, query));
    }

    private static boolean isTargetPresentInUrlPart(String url, String field, boolean query) {
        String part = urlPart(url, query);
        return part != null && part.toLowerCase(Locale.ROOT).contains(field.toLowerCase(Locale.ROOT));
    }

    private static String urlPart(String url, boolean query) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            return query ? uri.getRawQuery() : uri.getRawPath();
        } catch (IllegalArgumentException _) {
            int querySeparator = url.indexOf('?');
            if (query) {
                return querySeparator < 0 ? null : url.substring(querySeparator + 1);
            }
            return querySeparator < 0 ? url : url.substring(0, querySeparator);
        }
    }

    private static String rootName(String field) {
        int separator = field.indexOf('#');
        return field.substring(0, separator < 0 ? field.length() : separator);
    }

    private static String simpleName(String field) {
        return field.substring(field.lastIndexOf('#') + 1);
    }

    private static List<RequestTarget> ordered(Collection<RequestTarget> targets) {
        return targets.stream()
                .sorted(RequestTarget.ordering())
                .toList();
    }

    private record RequestState(String path, String bodyPayload, String parameterPayload,
                                Map<String, List<String>> headers) {
        private static RequestState from(FuzzingData data) {
            boolean bodyMethod = HttpMethod.requiresBody(data.getMethod());
            return new RequestState(data.getPath(), bodyMethod ? data.getPayload() : null,
                    bodyMethod ? data.getPathParamsPayload() : data.getPayload(), headers(data.getHeaders()));
        }

        private static RequestState from(FuzzingData data, String payload, String parameterPayload,
                                         Collection<CatsHeader> headers) {
            boolean bodyMethod = HttpMethod.requiresBody(data.getMethod());
            return new RequestState(data.getPath(), bodyMethod ? payload : null,
                    bodyMethod ? parameterPayload : payload, headers(headers));
        }

        private static RequestState from(FuzzingData data, CatsRequest request, String parameterPayload) {
            boolean bodyMethod = HttpMethod.requiresBody(data.getMethod());
            return new RequestState(request.getUrl(), bodyMethod ? request.getPayload() : null,
                    parameterPayload, headersFromRequest(request.getHeaders()));
        }

        private static Map<String, List<String>> headers(Collection<CatsHeader> headers) {
            Map<String, List<String>> values = caseInsensitiveMap();
            Objects.requireNonNullElse(headers, List.<CatsHeader>of()).forEach(header ->
                    values.computeIfAbsent(header.getName(), _ -> new ArrayList<>()).add(header.getValue()));
            return values;
        }

        private static Map<String, List<String>> headersFromRequest(
                Collection<KeyValuePair<String, Object>> headers) {
            Map<String, List<String>> values = caseInsensitiveMap();
            Objects.requireNonNullElse(headers, List.<KeyValuePair<String, Object>>of()).forEach(header ->
                    values.computeIfAbsent(header.getKey(), _ -> new ArrayList<>())
                            .add(Objects.toString(header.getValue(), null)));
            return values;
        }

        private static Map<String, List<String>> caseInsensitiveMap() {
            return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        }
    }
}
