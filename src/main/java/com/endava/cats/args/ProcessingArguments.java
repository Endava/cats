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
    @Value("${sanitizationStrategy:sanitizeAndValidate}")
    private String sanitizationStrategy;
    @Value("${useExamples:true}")
    private String useExamples;

    @Value("${arg.processing.fieldsFuzzingStrategy.help:help}")
    private String fieldsFuzzingStrategyHelp;
    @Value("${arg.processing.maxFieldsToRemove.help:help}")
    private String maxFieldsToRemoveHelp;
    @Value("${arg.processing.edgeSpacesStrategy.help:help}")
    private String edgeSpacesStrategyHelp;
    @Value("${arg.processing.sanitizationStrategy.help:help}")
    private String sanitizationStrategyHelp;
    @Value("${arg.processing.useExamples.help:help}")
    private String useExamplesHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("fieldsFuzzingStrategy").value(fieldsFuzzingStrategy).help(fieldsFuzzingStrategyHelp).build());
        args.add(CatsArg.builder().name("maxFieldsToRemove").value(maxFieldsToRemove).help(maxFieldsToRemoveHelp).build());
        args.add(CatsArg.builder().name("edgeSpacesStrategy").value(edgeSpacesStrategy).help(edgeSpacesStrategyHelp).build());
        args.add(CatsArg.builder().name("sanitizationStrategy").value(sanitizationStrategy).help(sanitizationStrategyHelp).build());
        args.add(CatsArg.builder().name("useExamples").value(useExamples).help(useExamplesHelp).build());
    }
}
