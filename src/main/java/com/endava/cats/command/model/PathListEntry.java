package com.endava.cats.command.model;

import com.endava.cats.http.HttpMethod;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathDetails that = (PathDetails) o;
            return Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public int compareTo(@NotNull PathListEntry.PathDetails o) {
            return this.getPath().compareTo(o.getPath());
        }
    }
}
