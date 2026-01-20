package com.endava.cats.command.model;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context object for passing configuration between commands.
 * This replaces direct mutation of parent command state by subcommands.
 * Provides type-safe methods for common configuration properties.
 */
public class CommandContext {
    private final Map<String, Object> properties = new HashMap<>();

    /**
     * Sets a property value.
     *
     * @param key   the property key
     * @param value the property value (null values are ignored)
     * @param <T>   the value type
     */
    public <T> void set(String key, T value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    /**
     * Gets a property value.
     *
     * @param key  the property key
     * @param type the expected value type
     * @param <T>  the value type
     * @return Optional containing the value if present and of correct type
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(properties.get(key))
                .filter(type::isInstance)
                .map(type::cast);
    }

    // Type-safe setters for common properties

    public void setFuzzerType(String fuzzerType) {
        set("fuzzerType", fuzzerType);
    }

    public Optional<String> getFuzzerType() {
        return get("fuzzerType", String.class);
    }

    public void setCustomFuzzerFile(File file) {
        set("customFuzzerFile", file);
    }

    public Optional<File> getCustomFuzzerFile() {
        return get("customFuzzerFile", File.class);
    }

    public void setSecurityFuzzerFile(File file) {
        set("securityFuzzerFile", file);
    }

    public Optional<File> getSecurityFuzzerFile() {
        return get("securityFuzzerFile", File.class);
    }

    public void setHeadersFile(File file) {
        set("headersFile", file);
    }

    public Optional<File> getHeadersFile() {
        return get("headersFile", File.class);
    }

    public void setHeadersMap(Map<String, Object> headers) {
        set("headersMap", headers);
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getHeadersMap() {
        return Optional.ofNullable(properties.get("headersMap"))
                .filter(Map.class::isInstance)
                .map(v -> (Map<String, Object>) v);
    }

    public void setContentType(String contentType) {
        set("contentType", contentType);
    }

    public Optional<String> getContentType() {
        return get("contentType", String.class);
    }

    public void setSeed(long seed) {
        set("seed", seed);
    }

    public Optional<Long> getSeed() {
        return get("seed", Long.class);
    }

    public void setCreateRefData(boolean createRefData) {
        set("createRefData", createRefData);
    }

    public Optional<Boolean> getCreateRefData() {
        return get("createRefData", Boolean.class);
    }

    public void setRefDataFile(File file) {
        set("refDataFile", file);
    }

    public Optional<File> getRefDataFile() {
        return get("refDataFile", File.class);
    }

    public void setQueryFile(File file) {
        set("queryFile", file);
    }

    public Optional<File> getQueryFile() {
        return get("queryFile", File.class);
    }

    public void setXxxOfSelections(Map<String, String> selections) {
        set("xxxOfSelections", selections);
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> getXxxOfSelections() {
        return Optional.ofNullable(properties.get("xxxOfSelections"))
                .filter(Map.class::isInstance)
                .map(v -> (Map<String, String>) v);
    }

    public void setPaths(List<String> paths) {
        set("paths", paths);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<String>> getPaths() {
        return Optional.ofNullable(properties.get("paths"))
                .filter(List.class::isInstance)
                .map(v -> (List<String>) v);
    }

    public void setHttpMethods(List<?> httpMethods) {
        set("httpMethods", httpMethods);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<?>> getHttpMethods() {
        return Optional.ofNullable(properties.get("httpMethods"))
                .filter(List.class::isInstance)
                .map(v -> (List<?>) v);
    }

    public void setSkipFuzzers(List<String> skipFuzzers) {
        set("skipFuzzers", skipFuzzers);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<String>> getSkipFuzzers() {
        return Optional.ofNullable(properties.get("skipFuzzers"))
                .filter(List.class::isInstance)
                .map(v -> (List<String>) v);
    }

    public void setSkipPaths(List<String> skipPaths) {
        set("skipPaths", skipPaths);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<String>> getSkipPaths() {
        return Optional.ofNullable(properties.get("skipPaths"))
                .filter(List.class::isInstance)
                .map(v -> (List<String>) v);
    }

    public void setIncludeContract(boolean includeContract) {
        set("includeContract", includeContract);
    }

    public Optional<Boolean> getIncludeContract() {
        return get("includeContract", Boolean.class);
    }

    public void setContract(String contract) {
        set("contract", contract);
    }

    public Optional<String> getContract() {
        return get("contract", String.class);
    }

    public void setServer(String server) {
        set("server", server);
    }

    public Optional<String> getServer() {
        return get("server", String.class);
    }

    public void setLimitXxxOfCombinations(int limit) {
        set("limitXxxOfCombinations", limit);
    }

    public Optional<Integer> getLimitXxxOfCombinations() {
        return get("limitXxxOfCombinations", Integer.class);
    }

    public void setFilesArguments(Object filesArguments) {
        set("filesArguments", filesArguments);
    }

    public Optional<Object> getFilesArguments() {
        return get("filesArguments", Object.class);
    }
}
