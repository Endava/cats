package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ProcessingArguments {
    @Value("${fieldsFuzzingStrategy:ONEBYONE}")
    private String fieldsFuzzingStrategy;
    @Value("${maxFieldsToRemove:empty}")
    private String maxFieldsToRemove;
    @Value("${edgeSpacesStrategy:trimAndValidate}")
    private String edgeSpacesStrategy;
    @Value("${useExamples:true}")
    private String useExamples;

}
