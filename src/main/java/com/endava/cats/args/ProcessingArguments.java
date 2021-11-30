package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Getter
public class ProcessingArguments {
    private final List<CatsArg> args = new ArrayList<>();

    private final String fieldsFuzzingStrategyHelp = "STRATEGY set the strategy for tge fields fuzzers. Supported strategies ONEBYONE, SIZE, POWERSET";
    private final String maxFieldsToRemoveHelp = "NUMBER set the maximum number of fields that will be removed from a request when using the SIZE fieldsFuzzingStrategy";
    private final String edgeSpacesStrategyHelp = "STRATEGY this can be either validateAndTrim or trimAndValidate. It can be used to specify what CATS should expect when sending trailing and leading spaces valid values within fields";
    private final String sanitizationStrategyHelp = "STRATEGY this can be either sanitizeAndValidation or validateAndSanitize. It can be used to specify what CATS should expect when sending Unicode Control Chars and Other Symbols within the fields";
    private final String useExamplesHelp = "true/false (default true), instruct CATS on whether to use examples from the OpenAPI contract or not";
    private final String largeStringsSizeHelp = "SIZE the size of the strings used by the Fuzzers sending large values like VeryLargeStringsFuzzer";

    @Value("${fieldsFuzzingStrategy:ONEBYONE}")
    private String fieldsFuzzingStrategy;
    @Value("${maxFieldsToRemove:empty}")
    private String maxFieldsToRemove;
    @Value("${edgeSpacesStrategy:trimAndValidate}")
    private String edgeSpacesStrategy;
    @Value("${sanitizationStrategy:sanitizeAndValidate}")
    private String sanitizationStrategy;
    @Value("${useExamples:true}")
    private String useExamples;
    @Value("${largeStringsSize:40000}")
    private int largeStringsSize;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("fieldsFuzzingStrategy").value(fieldsFuzzingStrategy).help(fieldsFuzzingStrategyHelp).build());
        args.add(CatsArg.builder().name("maxFieldsToRemove").value(maxFieldsToRemove).help(maxFieldsToRemoveHelp).build());
        args.add(CatsArg.builder().name("edgeSpacesStrategy").value(edgeSpacesStrategy).help(edgeSpacesStrategyHelp).build());
        args.add(CatsArg.builder().name("sanitizationStrategy").value(sanitizationStrategy).help(sanitizationStrategyHelp).build());
        args.add(CatsArg.builder().name("useExamples").value(useExamples).help(useExamplesHelp).build());
        args.add(CatsArg.builder().name("largeStringsSize").value(String.valueOf(largeStringsSize)).help(largeStringsSizeHelp).build());
    }
}
