package com.endava.cats.command.model;

import com.endava.cats.fuzzer.api.Fuzzer;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an entry in a fuzzer list, including a category and a list of fuzzer details.
 */
@Getter
public class FuzzerListEntry {
    private String category;
    private List<FuzzerDetails> fuzzers = new ArrayList<>();

    /**
     * Sets the category of the fuzzer list entry.
     *
     * @param category the category to set
     * @return this {@code FuzzerListEntry} for method chaining
     */
    public FuzzerListEntry category(String category) {
        this.category = category;
        return this;
    }

    /**
     * Sets the list of fuzzer details based on the provided list of fuzzers.
     *
     * @param fuzzersList the list of fuzzers to set details for
     * @return this {@code FuzzerListEntry} for method chaining
     */
    public FuzzerListEntry fuzzers(List<Fuzzer> fuzzersList) {
        this.fuzzers = fuzzersList.stream()
                .map(fuzzer -> FuzzerDetails.builder()
                        .name(fuzzer.getClass().getSimpleName())
                        .description(fuzzer.description())
                        .build())
                .collect(Collectors.toList());
        return this;
    }

    /**
     * Represents details about a fuzzer, including its name and description.
     */
    @Builder
    public static class FuzzerDetails {
        private String name;
        private String description;
    }
}
