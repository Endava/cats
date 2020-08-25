package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@ToString
@Getter
public class CatsSkipped {

    private String fuzzer;
    private List<String> forPaths;
}
