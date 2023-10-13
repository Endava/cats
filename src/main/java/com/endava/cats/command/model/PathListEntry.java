package com.endava.cats.command.model;

import com.endava.cats.http.HttpMethod;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Builder
@Getter
public class PathListEntry {
    private int numberOfPaths;
    private int numberOfOperations;
    private List<PathDetails> pathDetailsList;

    @Builder
    @Getter
    public static class PathDetails implements Comparable<PathDetails> {
        private List<HttpMethod> methods;
        private String path;
        
        @Override
        public int compareTo(@NotNull PathListEntry.PathDetails o) {
            return this.getPath().compareTo(o.getPath());
        }
    }
}
