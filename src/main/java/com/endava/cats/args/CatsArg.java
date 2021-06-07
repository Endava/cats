package com.endava.cats.args;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public final class CatsArg {
    private final String name;
    private final String help;
    private final String value;
}
