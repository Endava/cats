package com.endava.cats.io.util;

import com.endava.cats.model.KeyValuePair;
import com.endava.cats.model.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FormEncoder {

    private FormEncoder() {
        //ntd
    }

    public static HttpContent createHttpContent(Map<String, Object> params) throws IOException {
        // If params is null, we create an empty HttpContent because we still want to send the
        // Content-Type header.
        if (params == null) {
            return HttpContent.buildFormURLEncodedContent(new ArrayList<>());
        }

        Collection<KeyValuePair<String, Object>> flatParams = flattenParams(params);

        // If all parameters have been encoded as strings, then the content can be represented
        // with application/x-www-form-url-encoded encoding. Otherwise, use
        // multipart/form-data encoding.
        if (flatParams.stream().allMatch(kvp -> kvp.getValue() instanceof String)) {
            Collection<KeyValuePair<String, String>> flatParamsString =
                    flatParams.stream()
                            .filter(kvp -> kvp.getValue() instanceof String)
                            .map(kvp -> new KeyValuePair<>(kvp.getKey(), (String) kvp.getValue()))
                            .toList();
            return HttpContent.buildFormURLEncodedContent(flatParamsString);
        } else {
            return HttpContent.buildMultipartFormDataContent(flatParams);
        }
    }

    /**
     * Creates the HTTP query string for a given map of parameters.
     *
     * @param params The map of parameters.
     * @return The query string.
     */
    public static String createQueryString(Map<String, Object> params) {
        if (params == null) {
            return "";
        }

        Collection<KeyValuePair<String, String>> flatParams =
                flattenParams(params).stream()
                        .filter(kvp -> kvp.getValue() instanceof String)
                        .map(kvp -> new KeyValuePair<>(kvp.getKey(), (String) kvp.getValue()))
                        .toList();
        return createQueryString(flatParams);
    }

    /**
     * Creates the HTTP query string for a collection of name/value tuples.
     *
     * @param nameValueCollection The collection of name/value tuples.
     * @return The query string.
     */
    public static String createQueryString(
            Collection<KeyValuePair<String, String>> nameValueCollection) {
        if (nameValueCollection == null) {
            return "";
        }

        return nameValueCollection.stream()
                .map(kvp -> String.format("%s=%s", urlEncode(kvp.getKey()), urlEncode(kvp.getValue())))
                .collect(Collectors.joining("&"));
    }

    /**
     * Returns a list of flattened parameters for the given map of parameters.
     *
     * <p>This is a "pre-encoding" step necessary to send requests to Stripe's API. Form encoding can
     * be ambiguous when it comes to nested parameters (lists or maps). Stripe's API relies heavily on
     * such parameters and expects them to be encoded in a certain way. This method takes a map of
     * parameters that can contain deeply nested parameters and return a flat list of key/value pairs.
     *
     * <p>Values are always encoded as {@link String}s, except for {@link File} and {@link
     * InputStream} values that are left as-is. When there is at least one {@link File} or {@link
     * InputStream} value, the request should be encoded using {@code multipart/form-data} MIME type;
     * otherwise (i.e. if all values are {@link String}s), the request should be encoded using {@code
     * application/x-www-form-urlencoded} MIME type.
     *
     * <pre>{@code
     * Map<String, Object> item1 = new HashMap<>() { put("plan", "gold"); };
     * Map<String, Object> item2 = new HashMap<>() { put("plan", "silver"); };
     * List<Map<String, Object>> items = new ArrayList<>() { add(item1); add(item2); };
     * Map<String, Object> params = new HashMap<>() { put("amount", 234); put("items", items); };
     *
     * List<KeyValuePair<String, Object>> flattenedParams = FormEncoder.flattenParams(params);
     * // flattenedParams is a list of KeyValuePair<String, Object> with 3 elements:
     * // 1. key="amount" value="234"
     * // 2. key="items[0][plan]" value="gold"
     * // 2. key="items[1][plan]" value="silver"
     * }</pre>
     *
     * @param params The map of parameters.
     * @return The flattened list of parameters.
     */
    public static List<KeyValuePair<String, Object>> flattenParams(Map<String, Object> params) {
        return flattenParamsValue(params, null);
    }

    /**
     * URL-encodes a string.
     *
     * @param value The string to URL-encode.
     * @return The URL-encoded string.
     */
    private static String urlEncode(String value) {
        if (value == null) {
            return null;
        }

        // Don't use strict form encoding by changing the square bracket control
        // characters back to their literals. This is fine by the server, and
        // makes these parameter strings easier to read.
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("%5B", "[")
                .replace("%5D", "]");
    }

    /**
     * Returns a list of parameters for a given value. The value can be basically anything, as long as
     * it can be encoded in some way.
     *
     * @param value     The value for which to create the list of parameters.
     * @param keyPrefix The key under which new keys should be nested, if any.
     * @return The list of parameters.
     */
    private static List<KeyValuePair<String, Object>> flattenParamsValue(
            Object value, String keyPrefix) {
        List<KeyValuePair<String, Object>> flatParams;

        // I wish Java had pattern matching :(

        if (value == null) {
            flatParams = singleParam(keyPrefix, "");

        } else if (value instanceof Map<?, ?>) {
            flatParams = flattenParamsMap((Map<?, ?>) value, keyPrefix);

        } else if (value instanceof String) {
            flatParams = singleParam(keyPrefix, value);

        } else if (value instanceof File) {
            flatParams = singleParam(keyPrefix, value);

        } else if (value instanceof InputStream) {
            flatParams = singleParam(keyPrefix, value);

        } else if (value instanceof Collection<?>) {
            flatParams = flattenParamsCollection((Collection<?>) value, keyPrefix);

        } else if (value.getClass().isArray()) {
            Object[] array = getArrayForObject(value);
            Collection<?> collection = Arrays.stream(array).toList();
            flatParams = flattenParamsCollection(collection, keyPrefix);

        } else if (value.getClass().isEnum()) {
            flatParams = singleParam(keyPrefix, JsonUtils.GSON.toJson(value).replace("\"", ""));

        } else {
            flatParams = singleParam(keyPrefix, value.toString());
        }

        return flatParams;
    }

    /**
     * Returns a list of parameters for a given map. If a key prefix is provided, the keys for the new
     * parameters will be nested under the key prefix. E.g. if the key prefix `foo` is passed and the
     * map contains a key `bar`, then a parameter with key `foo[bar]` will be returned.
     *
     * @param map       The map for which to create the list of parameters.
     * @param keyPrefix The key under which new keys should be nested, if any.
     * @return The list of parameters.
     */
    private static List<KeyValuePair<String, Object>> flattenParamsMap(
            Map<?, ?> map, String keyPrefix) {
        List<KeyValuePair<String, Object>> flatParams = new ArrayList<>();
        if (map == null) {
            return flatParams;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            String newPrefix = newPrefix(key, keyPrefix);

            flatParams.addAll(flattenParamsValue(value, newPrefix));
        }

        return flatParams;
    }

    /**
     * Returns a list of parameters for a given collection of objects. The parameter keys will be
     * indexed under the `keyPrefix` parameter. E.g. if the `keyPrefix` is `foo`, then the key for the
     * first element's will be `foo[0]`, etc.
     *
     * @param collection The collection for which to create the list of parameters.
     * @param keyPrefix  The key under which new keys should be nested.
     * @return The list of parameters.
     */
    private static List<KeyValuePair<String, Object>> flattenParamsCollection(
            Collection<?> collection, String keyPrefix) {
        List<KeyValuePair<String, Object>> flatParams = new ArrayList<>();
        if (collection == null) {
            return flatParams;
        }

        int index = 0;
        for (Object value : collection) {
            String newPrefix = String.format("%s[%d]", keyPrefix, index);
            flatParams.addAll(flattenParamsValue(value, newPrefix));
            index += 1;
        }

        /* Because application/x-www-form-urlencoded cannot represent an empty list, convention
         * is to take the list parameter and just set it to an empty string. (E.g. A regular
         * list might look like `a[0]=1&b[1]=2`. Emptying it would look like `a=`.) */
        if (flatParams.isEmpty()) {
            flatParams.add(new KeyValuePair<>(keyPrefix, ""));
        }

        return flatParams;
    }

    /**
     * Creates a list containing a single parameter.
     *
     * @param key   The parameter's key.
     * @param value The parameter's value.
     * @return A list containing the single parameter.
     */
    private static List<KeyValuePair<String, Object>> singleParam(String key, Object value) {
        List<KeyValuePair<String, Object>> flatParams = new ArrayList<>();
        flatParams.add(new KeyValuePair<>(key, value));
        return flatParams;
    }

    /**
     * Computes the new key prefix, given a key and an existing prefix (if any). E.g. if the key is
     * `bar` and the existing prefix is `foo`, then `foo[bar]` is returned.
     *
     * <p>If a key already contains nested values, then only the non-nested part is nested under the
     * prefix, e.g. if the key is `bar[baz]` and the prefix is `foo`, then `foo[bar][baz]` is
     * returned.
     *
     * <p>If no prefix is provided, the key is returned unchanged.
     *
     * @param key       The key.
     * @param keyPrefix The existing key prefix, if any.
     * @return The new key prefix.
     */
    private static String newPrefix(String key, String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return key;
        }

        int i = key.indexOf("[");
        if (i == -1) {
            return String.format("%s[%s]", keyPrefix, key);
        } else {
            return String.format("%s[%s][%s]", keyPrefix, key.substring(0, i), key.substring(i));
        }
    }

    /**
     * Accepts an object (that must be an array) and returns an `Object[]`. If the array already holds
     * reference types, it is simply cast to `Object[]`. If the array holds primitive types, a new
     * array is created with elements copied and boxed to the appropriate wrapper class. E.g. an
     * `int[]` array will be returned as an `Integer[]` array.
     *
     * @param array The original array, as an Object.
     * @return The Object[] array.
     */
    private static Object[] getArrayForObject(Object array) {
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("parameter is not an array");
        }

        // If element type is not a primitive, simply cast the object and return
        if (!array.getClass().getComponentType().isPrimitive()) {
            return (Object[]) array;
        }

        // Otherwise, initialize a new array of Object and copy elements one by one. Primitive
        // elements will be autoboxed.
        int length = Array.getLength(array);
        Object[] newArray = new Object[length];

        for (int index = 0; index < length; index++) {
            newArray[index] = Array.get(array, index);
        }

        return newArray;
    }
}

