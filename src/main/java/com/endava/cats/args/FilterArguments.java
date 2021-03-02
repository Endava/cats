package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class FilterArguments {
    @Value("${fuzzers:all}")
    private String suppliedFuzzers;
    @Value("${paths:all}")
    private String paths;
    @Value("${excludedFuzzers:empty}")
    private String excludedFuzzers;
}
