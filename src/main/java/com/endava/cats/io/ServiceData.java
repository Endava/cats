package com.endava.cats.io;

import com.endava.cats.model.CatsHeader;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Builder(access = AccessLevel.PUBLIC)
@Getter
public class ServiceData {
    private String relativePath;
    private Collection<CatsHeader> headers;
    private String payload;
    @Builder.Default
    private boolean replaceRefData = true;
    @Builder.Default
    private boolean addUserHeaders = true;
    @Builder.Default
    private Set<String> skippedHeaders = new HashSet<>();
    @Singular
    private Set<String> fuzzedFields;
    @Singular
    private Set<String> fuzzedHeaders;
    @Builder.Default
    private Set<String> pathParams = new HashSet<>();
}
