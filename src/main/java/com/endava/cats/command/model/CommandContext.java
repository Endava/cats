package com.endava.cats.command.model;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed context object for passing configuration between subcommands and {@code CatsCommand}.
 */
public class CommandContext {
    private String fuzzerType;
    private File customFuzzerFile;
    private File functionalFlowFile;
    private File correlationsFile;
    private File securityFuzzerFile;
    private File headersFile;
    private Map<String, Object> headersMap;
    private String contentType;
    private Long seed;
    private Boolean createRefData;
    private File refDataFile;
    private File queryFile;
    private Map<String, String> xxxOfSelections;
    private String discriminatorCasing;
    private List<String> paths;
    private List<HttpMethod> httpMethods;
    private List<String> skipFuzzers;
    private List<String> skipPaths;
    private List<HttpMethod> skippedHttpMethods;
    private Boolean includeContract;
    private String contract;
    private String server;
    private Integer limitXxxOfCombinations;
    private FilesArguments filesArguments;
    private FilterArguments.TotalCountType totalCountType;

    public void setFuzzerType(String fuzzerType) {
        this.fuzzerType = fuzzerType;
    }

    public Optional<String> getFuzzerType() {
        return Optional.ofNullable(fuzzerType);
    }

    public void setCustomFuzzerFile(File file) {
        this.customFuzzerFile = file;
    }

    public Optional<File> getCustomFuzzerFile() {
        return Optional.ofNullable(customFuzzerFile);
    }

    public void setFunctionalFlowFile(File file) {
        this.functionalFlowFile = file;
    }

    public Optional<File> getFunctionalFlowFile() {
        return Optional.ofNullable(functionalFlowFile);
    }

    public void setCorrelationsFile(File file) {
        this.correlationsFile = file;
    }

    public Optional<File> getCorrelationsFile() {
        return Optional.ofNullable(correlationsFile);
    }

    public void setSecurityFuzzerFile(File file) {
        this.securityFuzzerFile = file;
    }

    public Optional<File> getSecurityFuzzerFile() {
        return Optional.ofNullable(securityFuzzerFile);
    }

    public void setHeadersFile(File file) {
        this.headersFile = file;
    }

    public Optional<File> getHeadersFile() {
        return Optional.ofNullable(headersFile);
    }

    public void setHeadersMap(Map<String, Object> headers) {
        this.headersMap = headers;
    }

    public Optional<Map<String, Object>> getHeadersMap() {
        return Optional.ofNullable(headersMap);
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public Optional<Long> getSeed() {
        return Optional.ofNullable(seed);
    }

    public void setCreateRefData(boolean createRefData) {
        this.createRefData = createRefData;
    }

    public Optional<Boolean> getCreateRefData() {
        return Optional.ofNullable(createRefData);
    }

    public void setRefDataFile(File file) {
        this.refDataFile = file;
    }

    public Optional<File> getRefDataFile() {
        return Optional.ofNullable(refDataFile);
    }

    public void setQueryFile(File file) {
        this.queryFile = file;
    }

    public Optional<File> getQueryFile() {
        return Optional.ofNullable(queryFile);
    }

    public void setXxxOfSelections(Map<String, String> selections) {
        this.xxxOfSelections = selections;
    }

    public Optional<Map<String, String>> getXxxOfSelections() {
        return Optional.ofNullable(xxxOfSelections);
    }

    public void setDiscriminatorCasing(String discriminatorCasing) {
        this.discriminatorCasing = discriminatorCasing;
    }

    public Optional<String> getDiscriminatorCasing() {
        return Optional.ofNullable(discriminatorCasing);
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public Optional<List<String>> getPaths() {
        return Optional.ofNullable(paths);
    }

    public void setHttpMethods(List<HttpMethod> httpMethods) {
        this.httpMethods = httpMethods;
    }

    public Optional<List<HttpMethod>> getHttpMethods() {
        return Optional.ofNullable(httpMethods);
    }

    public void setSkipFuzzers(List<String> skipFuzzers) {
        this.skipFuzzers = skipFuzzers;
    }

    public Optional<List<String>> getSkipFuzzers() {
        return Optional.ofNullable(skipFuzzers);
    }

    public void setSkipPaths(List<String> skipPaths) {
        this.skipPaths = skipPaths;
    }

    public Optional<List<String>> getSkipPaths() {
        return Optional.ofNullable(skipPaths);
    }

    public void setSkippedHttpMethods(List<HttpMethod> skippedHttpMethods) {
        this.skippedHttpMethods = skippedHttpMethods;
    }

    public Optional<List<HttpMethod>> getSkippedHttpMethods() {
        return Optional.ofNullable(skippedHttpMethods);
    }

    public void setIncludeContract(boolean includeContract) {
        this.includeContract = includeContract;
    }

    public Optional<Boolean> getIncludeContract() {
        return Optional.ofNullable(includeContract);
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public Optional<String> getContract() {
        return Optional.ofNullable(contract);
    }

    public void setServer(String server) {
        this.server = server;
    }

    public Optional<String> getServer() {
        return Optional.ofNullable(server);
    }

    public void setLimitXxxOfCombinations(int limit) {
        this.limitXxxOfCombinations = limit;
    }

    public Optional<Integer> getLimitXxxOfCombinations() {
        return Optional.ofNullable(limitXxxOfCombinations);
    }

    public void setFilesArguments(FilesArguments filesArguments) {
        this.filesArguments = filesArguments;
    }

    public Optional<FilesArguments> getFilesArguments() {
        return Optional.ofNullable(filesArguments);
    }

    public void setTotalCountType(FilterArguments.TotalCountType totalCountType) {
        this.totalCountType = totalCountType;
    }

    public Optional<FilterArguments.TotalCountType> getTotalCountType() {
        return Optional.ofNullable(totalCountType);
    }
}
