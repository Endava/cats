package com.endava.cats.command.model;

import com.endava.cats.http.HttpMethod;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

/**
 * Represents an entry in a path list, including the number of paths, number of operations, and a list of path details.
 */
@Builder
@Getter
public class PathListEntry {
    private int numberOfPaths;
    private int numberOfOperations;
    private List<PathDetails> pathDetailsList;

    /**
     * Represents details about a path, including the HTTP methods and path itself.
     */
    @Builder
    @Getter
    @EqualsAndHashCode(of = "path")
    public static class PathDetails implements Comparable<PathDetails> {
        private List<HttpMethod> methods;
        private String path;

        @Override
        public int compareTo(PathListEntry.PathDetails o) {
            return this.getPath().compareTo(o.getPath());
        }
    }
}
