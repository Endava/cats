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

    @Value("${fieldsFuzzingStrategy:ONEBYONE}")
    private String fieldsFuzzingStrategy;
    @Value("${maxFieldsToRemove:empty}")
    private String maxFieldsToRemove;
    @Value("${edgeSpacesStrategy:trimAndValidate}")
    private String edgeSpacesStrategy;
    @Value("${useExamples:true}")
    private String useExamples;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("fieldsFuzzingStrategy").value(fieldsFuzzingStrategy).help("STRATEGY set the strategy for tge fields fuzzers. Supported strategies ONEBYONE, SIZE, POWERSET").build());
        args.add(CatsArg.builder().name("maxFieldsToRemove").value(maxFieldsToRemove).help("NUMBER set the maximum number of fields that will be removed from a request when using the SIZE fieldsFuzzingStrategy").build());
        args.add(CatsArg.builder().name("edgeSpacesStrategy").value(edgeSpacesStrategy).help("STRATEGY this can be either validateAndTrim or trimAndValidate. It can be used to specify what CATS should expect when sending trailing and leading spaces valid values within fields").build());
        args.add(CatsArg.builder().name("useExamples").value(useExamples).help("true/false (default true), instruct CATS on whether to use examples from the OpenAPI contract or not").build());
    }
}
