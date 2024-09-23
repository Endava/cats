package com.endava.cats.args;

import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds arguments related to different customizations supported by CATS when running the Fuzzers.
 */
@Singleton
@Getter
public class ProcessingArguments {
    @CommandLine.Option(names = {"--fieldsFuzzingStrategy"},
            description = "The strategy used by some of the field fuzzers. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private SetFuzzingStrategy fieldsFuzzingStrategy = SetFuzzingStrategy.ONEBYONE;

    @CommandLine.Option(names = {"--maxFieldsToRemove"},
            description = "The maximum number of fields that will be removed from a request when using the @|bold,underline SIZE|@ fieldsFuzzingStrategy")
    private int maxFieldsToRemove;

    @CommandLine.Option(names = {"--edgeSpacesStrategy"},
            description = "This can be either @|bold,underline VALIDATE_AND_TRIM|@ or @|bold,underline TRIM_AND_VALIDATE|@. It can be used to specify what CATS should expect when sending trailing and leading spaces valid values within fields. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private TrimmingStrategy edgeSpacesStrategy = TrimmingStrategy.TRIM_AND_VALIDATE;

    @CommandLine.Option(names = {"--sanitizationStrategy"},
            description = "This can be either @|bold,underline SANITIZE_AND_VALIDATE|@ or @|bold,underline VALIDATE_AND_SANITIZE|@. It can be used to specify what CATS should expect when sending Unicode Control Chars and Other Symbols within the fields. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private SanitizationStrategy sanitizationStrategy = SanitizationStrategy.SANITIZE_AND_VALIDATE;

    @CommandLine.Option(names = {"--useExamples"}, negatable = true, defaultValue = "true", fallbackValue = "true",
            description = "When set to @|bold true|@, it will use examples from the OpenAPI contract when available. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean useExamples = true;

    @CommandLine.Option(names = {"--cachePayloads"}, negatable = true, defaultValue = "true", fallbackValue = "true",
            description = "When set to @|bold true|@, it will cache payload examples for same schema name instead of generating new ones for each occurrence. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean cachePayloads = true;

    @CommandLine.Option(names = {"--largeStringsSize"},
            description = "The size of the strings used by the Fuzzers sending large values like @|bold VeryLargeStringsFuzzer|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int largeStringsSize = 40000;

    @CommandLine.Option(names = {"--randomHeadersNumber"},
            description = "The number of random headers that will be sent by the @|bold LargeNumberOfRandomAlphanumericHeadersFuzzer and LargeNumberOfRandomHeadersFuzzer|@. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int randomHeadersNumber = 10000;

    @CommandLine.Option(names = {"--selfReferenceDepth", "-L"},
            description = "Max depth for request objects having cyclic dependencies. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int selfReferenceDepth = 4;

    @Setter
    @CommandLine.Option(names = {"--contentType"},
            description = "A custom mime type if the OpenAPI contract/spec uses content type negotiation versioning")
    private String contentType;

    @Setter
    @CommandLine.Option(names = {"--oneOfSelection", "--anyOfSelection"},
            description = "A @|bold name=value|@ list of discriminator names and values that can be use to filter request payloads when objects use oneOf or anyOf definitions" +
                    " which result in multiple payloads for a single endpoint and http method")
    Map<String, String> xxxOfSelections;

    @Setter
    @CommandLine.Option(names = {"--rfc7396"},
            description = "When set to @|bold true|@, it will send Content-Type=application/merge-patch+json for PATCH requests. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean rfc7396;

    @Setter
    @CommandLine.Option(names = {"--allowInvalidEnumValues"},
            description = "When set to @|bold true|@, the InvalidValuesInEnumsFieldsFuzzer will expect a 2XX response code instead of 4XX. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean allowInvalidEnumValues;

    @CommandLine.Option(names = {"--limitXxxOfCombinations"},
            description = "Max number of anyOf/oneOf combinations. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private int limitXxxOfCombinations = 20;

    @CommandLine.Option(names = {"--generateXxxCombinationsForResponses"},
            description = "Generate anyOf/oneOf combinations also for response schemas. By default it creates one response payload with all possibilities. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean generateAllXxxCombinationsForResponses;

    @Setter
    @CommandLine.Option(names = {"--filterXxxFromRequestPayloads"}, negatable = true, defaultValue = "true", fallbackValue = "true",
            description = "In extremely rare cases when CATS fails to generate anyOf/oneOf combinations some requests may still contain ONE_OF/ANY_OF markers. They are filtered out by default. " +
                    "Setting this to false will send them as requests which will probably fail. It's mostly for debug purposes. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean filterXxxFromRequestPayloads = true;

    @CommandLine.Option(names = {"--useDefaults"}, negatable = true, defaultValue = "true", fallbackValue = "true",
            description = "If set to @|bold true|@, it will use default values (if set) when generating examples. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean useDefaults = true;

    /**
     * Represents a wildcard pattern for JSON content type with optional parameters.
     */
    public static final String JSON_WILDCARD = "application\\/.*\\+?json;?.*";
    /**
     * Represents the JSON Patch content type.
     */
    public static final String JSON_PATCH = "application/merge-patch+json";

    /**
     * Checks if the payload matches any of the supplied --oneOfSelection or --anyOfSelection argument
     *
     * @param payload the request payload
     * @return true if payload matches on the arguments, false otherwise
     */
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

    /**
     * Gets the supplied content type of "application/json" and "application/x-www-form-urlencoded" if not supplied.
     *
     * @return the --contentType argument if supplied, or a default list otherwise.
     */
    public List<String> getContentType() {
        if (contentType == null) {
            return List.of(JSON_WILDCARD, "application/x-www-form-urlencoded");
        }
        return List.of(contentType);
    }

    /**
     * Returns the supplied --contentType or "application/json" if not supplied.
     *
     * @return supplied --contentType or "application/json" if not supplied
     */
    public String getDefaultContentType() {
        return Optional.ofNullable(contentType).orElse("application/json");
    }

    /**
     * The possible strategies used by services to handle trimming.
     */
    public enum TrimmingStrategy {
        /**
         * Services will first validate then trim.
         */
        VALIDATE_AND_TRIM,
        /**
         * Services will first trim then validate. This is default.
         */
        TRIM_AND_VALIDATE
    }

    /**
     * The possible strategies used by services to handle sanitization.
     */
    public enum SanitizationStrategy {
        /**
         * Services will first validate and then sanitize.
         */
        VALIDATE_AND_SANITIZE,

        /**
         * Services will first sanitize and then validate. This is default.
         */
        SANITIZE_AND_VALIDATE
    }

    /**
     * Fuzzing strategy when removing fields.
     */
    public enum SetFuzzingStrategy {
        /**
         * Use all possible combinations.
         */
        POWERSET,

        /**
         * Use possible combinations up to a size.
         */
        SIZE,
        /**
         * Remove one by one.
         */
        ONEBYONE
    }
}
