package com.endava.cats.args;

import com.endava.cats.json.JsonUtils;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@Getter
public class ProcessingArguments {
    @CommandLine.Option(names = {"--fieldsFuzzingStrategy"},
            description = "The strategy for the fields fuzzers. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private SetFuzzingStrategy fieldsFuzzingStrategy = SetFuzzingStrategy.ONEBYONE;

    @CommandLine.Option(names = {"--maxFieldsToRemove"},
            description = "The maximum number of fields that will be removed from a request when using the @|bold,underline SIZE|@ fieldsFuzzingStrategy")
    private int maxFieldsToRemove;

    @CommandLine.Option(names = {"--edgeSpacesStrategy"},
            description = "This can be either @|bold,underline VALIDATE_AND_TRIM|@ or @|bold,underline TRIM_AND_VALIDATE|@. It can be used to specify what CATS should expect when sending trailing and leading spaces valid values within fields. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private TrimmingStrategy edgeSpacesStrategy = TrimmingStrategy.TRIM_AND_VALIDATE;

    @CommandLine.Option(names = {"--sanitizationStrategy"},
            description = "This can be either sanitizeAndValidation or validateAndSanitize. It can be used to specify what CATS should expect when sending Unicode Control Chars and Other Symbols within the fields. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private SanitizationStrategy sanitizationStrategy = SanitizationStrategy.SANITIZE_AND_VALIDATE;

    @CommandLine.Option(names = {"--useExamples"}, negatable = true,
            description = "Use examples from the OpenAPI contract or not. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean useExamples = true;

    @CommandLine.Option(names = {"--largeStringsSize"},
            description = "The size of the strings used by the Fuzzers sending large values like @|bold VeryLargeStringsFuzzer|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int largeStringsSize = 40000;
    @CommandLine.Option(names = {"--randomHeadersNumber"},
            description = "The number of random headers that will be sent by the @|bold LargeNumberOfRandomAlphanumericHeadersFuzzer and LargeNumberOfRandomHeadersFuzzer|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int randomHeadersNumber = 10000;

    @CommandLine.Option(names = {"--selfReferenceDepth", "-L"},
            description = "Max depth for objects having cyclic dependencies. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int selfReferenceDepth = 3;

    @Setter
    @CommandLine.Option(names = {"--contentType"},
            description = "A custom mime type if the OpenAPI spec uses content type negotiation versioning.")
    private String contentType;

    @CommandLine.Option(names = {"--oneOfSelection", "--anyOfSelection"},
            description = "A @|bold name:value|@ list of discriminator names and values that can be use to filter request payloads when objects use oneOf or anyOf definitions" +
                    " which result in multiple payloads for a single endpoint and http method.")
    Map<String, String> xxxOfSelections;

    public static final String JSON_WILDCARD = "application\\/.*\\+?json;?.*";

    public boolean matchesXxxSelection(String payload) {
        if (xxxOfSelections != null) {
            return xxxOfSelections.entrySet().stream().anyMatch(entry ->
            {
                String valueFromPayload = String.valueOf(JsonUtils.getVariableFromJson(payload, entry.getKey()));
                return JsonUtils.isNotSet(valueFromPayload) || valueFromPayload.equalsIgnoreCase(entry.getValue());
            });
        }
        return true;
    }

    public List<String> getContentType() {
        if (contentType == null) {
            return List.of(JSON_WILDCARD, "application/x-www-form-urlencoded");
        }
        return List.of(contentType);
    }

    public String getDefaultContentType() {
        return Optional.ofNullable(contentType).orElse("application/json");
    }

    public enum TrimmingStrategy {
        VALIDATE_AND_TRIM, TRIM_AND_VALIDATE
    }

    public enum SanitizationStrategy {
        VALIDATE_AND_SANITIZE, SANITIZE_AND_VALIDATE
    }

    public enum SetFuzzingStrategy {
        POWERSET, SIZE, ONEBYONE
    }
}
