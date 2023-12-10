package com.endava.cats.command.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Builder
@Getter
public class PathDetailsEntry {
    private String path;
    private List<OperationDetails> operations;

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
