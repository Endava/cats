package com.endava.cats.command.model;

import lombok.Builder;

@Builder
public class PathListEntry {
    private String method;
    private String path;
}
