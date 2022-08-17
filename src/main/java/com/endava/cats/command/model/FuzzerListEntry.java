package com.endava.cats.command.model;

import com.endava.cats.Fuzzer;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FuzzerListEntry {
    private String category;
    private List<FuzzerDetails> fuzzers = new ArrayList<>();

    public FuzzerListEntry category(String category) {
        this.category = category;
        return this;
    }

    public FuzzerListEntry fuzzers(List<Fuzzer> fuzzersList) {
        this.fuzzers = fuzzersList.stream()
                .map(fuzzer -> FuzzerDetails.builder()
                        .name(fuzzer.getClass().getSimpleName())
                        .description(fuzzer.description())
                        .build())
                .collect(Collectors.toList());
        return this;
    }


    @Builder
    public static class FuzzerDetails {
        private String name;
        private String description;
    }
}
