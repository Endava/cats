package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Restricts follow-up requests to observed, same-origin URLs under the configured API base path.
 */
final class ObservedHttpTarget {
    private ObservedHttpTarget() {
    }

    /** Returns the API-relative path only when the target is safe to replay against the configured server. */
    static Optional<String> relativePath(ApiArguments arguments, String target) {
        if (StringUtils.isBlank(target) || StringUtils.isBlank(arguments.getServer())) {
            return Optional.empty();
        }
        try {
            URI base = URI.create(arguments.getServer());
            URI uri = URI.create(target).normalize();
            if (!isSameOrigin(base, uri) || hasUnsafeComponents(uri)) {
                return Optional.empty();
            }
            String basePath = base.getRawPath();
            String prefix = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
            return uri.getRawPath().startsWith(prefix + "/")
                    ? Optional.of(uri.getRawPath().substring(prefix.length())) : Optional.empty();
        } catch (IllegalArgumentException | NullPointerException _) {
            return Optional.empty();
        }
    }

    private static boolean isSameOrigin(URI base, URI target) {
        return List.of("http", "https").contains(base.getScheme()) &&
                base.getScheme().equalsIgnoreCase(target.getScheme()) &&
                base.getHost().equalsIgnoreCase(target.getHost()) && port(base) == port(target);
    }

    private static boolean hasUnsafeComponents(URI target) {
        return target.getUserInfo() != null || target.getRawQuery() != null || target.getRawFragment() != null ||
                target.getRawPath() == null || target.getRawPath().matches("(?i).*%(?:2f|5c|2e).*");
    }

    /**
     * Returns the item path only when a POST 201 Location or a POST 200/201 JSON response body identifier
     * proves that this run created that exact item.
     */
    static Optional<String> ownedPath(RuntimeResourcePool pool, ApiArguments arguments, String itemTemplate,
                                      String itemUrl) {
        int index = itemTemplate.lastIndexOf("/{");
        if (index < 1 || !itemTemplate.endsWith("}")) {
            return Optional.empty();
        }
        String collection = itemTemplate.substring(0, index);
        String parameterName = itemTemplate.substring(index + 2, itemTemplate.length() - 1);
        Optional<String> target = relativePath(arguments, itemUrl);
        if (target.isEmpty() || !matchesTemplate(itemTemplate, target.get())) {
            return Optional.empty();
        }
        return pool.successfulExchanges(collection, HttpMethod.POST).stream()
                .map(exchange -> createdItemPath(arguments, collection, parameterName, exchange))
                .flatMap(Optional::stream)
                .filter(target.get()::equals).findFirst();
    }

    private static Optional<String> createdItemPath(ApiArguments arguments, String collectionTemplate,
                                                    String parameterName,
                                                    RuntimeResourcePool.SuccessfulExchange exchange) {
        if (hasCreatedResourceLocation(exchange)) {
            Optional<String> fromLocation = resolvedLocation(exchange.url(), exchange.location())
                    .flatMap(url -> relativePath(arguments, url));
            if (fromLocation.isPresent()) {
                return fromLocation;
            }
        }
        if ((exchange.status() != 200 && exchange.status() != 201) || !JsonUtils.isValidJson(exchange.responseBody())) {
            return Optional.empty();
        }
        Optional<String> collectionPath = relativePath(arguments, exchange.url())
                .map(path -> path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path)
                .filter(path -> matchesTemplate(collectionTemplate, path));
        if (collectionPath.isEmpty()) {
            return Optional.empty();
        }
        return extractCreatedId(exchange.responseBody(), collectionTemplate, parameterName)
                .map(id -> collectionPath.get() + "/" + id);
    }

    private static Optional<String> extractCreatedId(String responseBody, String collectionTemplate, String parameterName) {
        try {
            JsonElement element = JsonUtils.parseAsJsonElement(responseBody);
            if (!element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            Set<String> candidateNames = candidateIdNames(collectionTemplate, parameterName);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()
                        && candidateNames.contains(normalize(entry.getKey()))) {
                    String value = entry.getValue().getAsString();
                    if (StringUtils.isNotBlank(value) && !value.contains("/")) {
                        return Optional.of(value);
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    private static Set<String> candidateIdNames(String collectionTemplate, String parameterName) {
        String resource = normalize(collectionTemplate.substring(collectionTemplate.lastIndexOf('/') + 1));
        String singular = resource.endsWith("ies") && resource.length() > 3
                ? resource.substring(0, resource.length() - 3) + "y"
                : (resource.endsWith("s") && !resource.endsWith("ss") && resource.length() > 1
                ? resource.substring(0, resource.length() - 1) : resource);
        return Set.copyOf(List.of(
                normalize(parameterName),
                "id",
                "uuid",
                "guid",
                resource + "id",
                singular + "id",
                resource + "uuid",
                singular + "uuid",
                resource + "guid",
                singular + "guid"
        ));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean hasCreatedResourceLocation(RuntimeResourcePool.SuccessfulExchange exchange) {
        return exchange.status() == 201 && exchange.location() != null;
    }

    /** Matches a concrete URL path to a documented OpenAPI path template, without crossing path segments. */
    static boolean matchesTemplate(String template, String path) {
        List<String> expected = Arrays.asList(template.split("/", -1));
        List<String> actual = Arrays.asList(path.split("/", -1));
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (isTemplateSegment(expected.get(i))) {
                if (actual.get(i).isBlank()) {
                    return false;
                }
            } else if (!expected.get(i).equals(actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTemplateSegment(String segment) {
        return segment.matches("\\{[^/]+}");
    }

    /** Resolves relative Location values against the original request URL before validating the destination. */
    static Optional<String> resolvedLocation(String requestUrl, String location) {
        try {
            return Optional.of(URI.create(requestUrl).resolve(URI.create(location)).toString());
        } catch (IllegalArgumentException | NullPointerException _) {
            return Optional.empty();
        }
    }

    private static int port(URI uri) {
        return uri.getPort() < 0 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
    }
}
