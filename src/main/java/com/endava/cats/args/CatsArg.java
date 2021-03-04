package com.endava.cats.args;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CatsArg {
    private String name;
    private String help;
    private String value;
}
