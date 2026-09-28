package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.RuntimeResourcePool;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    /** Returns the item path only when a POST 201 Location proves that this run created that exact item. */
    static Optional<String> ownedPath(RuntimeResourcePool pool, ApiArguments arguments, String itemTemplate,
                                      String itemUrl) {
        int index = itemTemplate.lastIndexOf("/{");
        if (index < 1 || !itemTemplate.endsWith("}")) {
            return Optional.empty();
        }
        String collection = itemTemplate.substring(0, index);
        Optional<String> target = relativePath(arguments, itemUrl);
        if (target.isEmpty() || !matchesTemplate(itemTemplate, target.get())) {
            return Optional.empty();
        }
        return pool.successfulExchanges(collection, HttpMethod.POST).stream()
                .filter(ObservedHttpTarget::hasCreatedResourceLocation)
                .map(exchange -> resolvedLocation(exchange.url(), exchange.location())
                        .flatMap(url -> relativePath(arguments, url)))
                .flatMap(Optional::stream)
                .filter(target.get()::equals).findFirst();
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
