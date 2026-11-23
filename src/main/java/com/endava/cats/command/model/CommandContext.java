package com.endava.cats.command.model;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;
import lombok.Setter;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed context object for passing configuration between subcommands and {@code CatsCommand}.
 */
@Setter
public class CommandContext {
    private String fuzzerType;
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
    private List<String> suppliedFuzzers;
    private List<String> paths;
    private List<HttpMethod> httpMethods;
    private List<String> skipFuzzers;
    private List<String> skipPaths;
    private List<HttpMethod> skippedHttpMethods;
    private List<String> tags;
    private List<String> skipTags;
    private List<String> operationIds;
    private List<String> skipOperationIds;
    private List<String> skipFuzzersForExtension;
    private Boolean skipDeprecated;
    private Boolean includeContract;
    private String contract;
    private String server;
    private Integer limitXxxOfCombinations;
    private FilesArguments filesArguments;
    private FilterArguments.TotalCountType totalCountType;

    public Optional<String> getFuzzerType() {
        return Optional.ofNullable(fuzzerType);
    }

    public Optional<File> getFunctionalFlowFile() {
        return Optional.ofNullable(functionalFlowFile);
    }

    public Optional<File> getCorrelationsFile() {
        return Optional.ofNullable(correlationsFile);
    }

    public Optional<File> getSecurityFuzzerFile() {
        return Optional.ofNullable(securityFuzzerFile);
    }

    public Optional<File> getHeadersFile() {
        return Optional.ofNullable(headersFile);
    }

    public Optional<Map<String, Object>> getHeadersMap() {
        return Optional.ofNullable(headersMap);
    }

    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    public Optional<Long> getSeed() {
        return Optional.ofNullable(seed);
    }

    public Optional<Boolean> getCreateRefData() {
        return Optional.ofNullable(createRefData);
    }

    public Optional<File> getRefDataFile() {
        return Optional.ofNullable(refDataFile);
    }

    public Optional<File> getQueryFile() {
        return Optional.ofNullable(queryFile);
    }

    public Optional<Map<String, String>> getXxxOfSelections() {
        return Optional.ofNullable(xxxOfSelections);
    }

    public Optional<String> getDiscriminatorCasing() {
        return Optional.ofNullable(discriminatorCasing);
    }

    public Optional<List<String>> getPaths() {
        return Optional.ofNullable(paths);
    }

    public Optional<List<HttpMethod>> getHttpMethods() {
        return Optional.ofNullable(httpMethods);
    }

    public Optional<List<String>> getSkipFuzzers() {
        return Optional.ofNullable(skipFuzzers);
    }

    public Optional<List<String>> getSkipPaths() {
        return Optional.ofNullable(skipPaths);
    }

    public Optional<List<HttpMethod>> getSkippedHttpMethods() {
        return Optional.ofNullable(skippedHttpMethods);
    }

    public Optional<Boolean> getIncludeContract() {
        return Optional.ofNullable(includeContract);
    }

    public Optional<String> getContract() {
        return Optional.ofNullable(contract);
    }

    public Optional<String> getServer() {
        return Optional.ofNullable(server);
    }

    public Optional<Integer> getLimitXxxOfCombinations() {
        return Optional.ofNullable(limitXxxOfCombinations);
    }

    public Optional<FilesArguments> getFilesArguments() {
        return Optional.ofNullable(filesArguments);
    }

    public Optional<FilterArguments.TotalCountType> getTotalCountType() {
        return Optional.ofNullable(totalCountType);
    }

    public Optional<List<String>> getSuppliedFuzzers() {
        return Optional.ofNullable(suppliedFuzzers);
    }

    public Optional<List<String>> getTags() {
        return Optional.ofNullable(tags);
    }

    public Optional<List<String>> getSkipTags() {
        return Optional.ofNullable(skipTags);
    }

    public Optional<List<String>> getOperationIds() {
        return Optional.ofNullable(operationIds);
    }

    public Optional<List<String>> getSkipOperationIds() {
        return Optional.ofNullable(skipOperationIds);
    }

    public Optional<List<String>> getSkipFuzzersForExtension() {
        return Optional.ofNullable(skipFuzzersForExtension);
    }

    public Optional<Boolean> getSkipDeprecated() {
        return Optional.ofNullable(skipDeprecated);
    }
}
