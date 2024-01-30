package com.endava.cats.command.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * Represents details about a path, including the path itself and a list of operation details.
 */
@Builder
@Getter
public class PathDetailsEntry {
    private String path;
    private List<OperationDetails> operations;

    /**
     * Represents details about an operation, including its operation ID, HTTP method,
     * responses, query parameters, and headers.
     */
    @Builder
    @Getter
    public static class OperationDetails {
        private String operationId;
        private String httpMethod;
        private Set<String> responses;
        private Set<String> queryParams;
        private Set<String> headers;
    }
}
