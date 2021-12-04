package com.endava.cats.args;

import com.endava.cats.model.FuzzingData;
import lombok.Getter;
import picocli.CommandLine;

import javax.inject.Singleton;

@Singleton
@Getter
public class ProcessingArguments {
    @CommandLine.Option(names = {"--fieldsFuzzingStrategy"},
            description = "The strategy for the fields fuzzers. Default: ${DEFAULT-VALUE}")
    private FuzzingData.SetFuzzingStrategy fieldsFuzzingStrategy = FuzzingData.SetFuzzingStrategy.ONEBYONE;

    @CommandLine.Option(names = {"--maxFieldsToRemove"},
            description = "The maximum number of fields that will be removed from a request when using the SIZE fieldsFuzzingStrategy")
    private int maxFieldsToRemove;

    @CommandLine.Option(names = {"--edgeSpacesStrategy"},
            description = "This can be either VALIDATE_AND_TRIM or TRIM_AND_VALIDATE. It can be used to specify what CATS should expect when sending trailing and leading spaces valid values within fields. Default: ${DEFAULT-VALUE}")
    private TrimmingStrategy edgeSpacesStrategy = TrimmingStrategy.TRIM_AND_VALIDATE;

    @CommandLine.Option(names = {"--sanitizationStrategy"},
            description = "This can be either sanitizeAndValidation or validateAndSanitize. It can be used to specify what CATS should expect when sending Unicode Control Chars and Other Symbols within the fields. Default: ${DEFAULT-VALUE}")
    private SanitizationStrategy sanitizationStrategy = SanitizationStrategy.SANITIZE_AND_VALIDATE;

    @CommandLine.Option(names = {"--useExamples"},
            description = "Instruct CATS on whether to use examples from the OpenAPI contract or not")
    private boolean useExamples;

    @CommandLine.Option(names = {"--largeStringsSize"},
            description = "The size of the strings used by the Fuzzers sending large values like VeryLargeStringsFuzzer. Default:  ${DEFAULT-VALUE}")
    private int largeStringsSize = 40000;

    public enum TrimmingStrategy {
        VALIDATE_AND_TRIM, TRIM_AND_VALIDATE;
    }

    public enum SanitizationStrategy {
        VALIDATE_AND_SANITIZE, SANITIZE_AND_VALIDATE;
    }

}
