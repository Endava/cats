package com.endava.cats.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@ToString
@Getter
@EqualsAndHashCode(of = {"fuzzer", "forPaths"})
public class CatsSkipped {

    private final String fuzzer;
    private final List<String> forPaths;
}
