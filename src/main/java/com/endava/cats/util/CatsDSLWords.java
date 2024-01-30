package com.endava.cats.util;

import java.util.List;
import java.util.Map;

/**
 * Holds constants referring CATS reserved words.
 */
public abstract class CatsDSLWords {
    /**
     * Typically used to flag that user selected all fuzzers or paths.
     */
    public static final String ALL = "all";
    /**
     * Reserved word used in CATS DSL files to describe the test case being run.
     */
    public static final String DESCRIPTION = "description";

    /**
     * Reserved word used in CATS DSL files to verify a boolean expression.
     */
    public static final String CHECK = "checkBoolean";

    /**
     * Reserved word used in CATS DSL files to verify a boolean expression.
     */
    public static final String CHECK_TRUE = "checkTrue";
    /**
     * Reserved word used in CATS DSL files to verify a boolean expression.
     */
    public static final String CHECK_FALSE = "checkFalse";

    public static final Map<String, String> CHECKS = Map.of(CHECK, "true", CHECK_TRUE, "true", CHECK_FALSE, "false");

    /**
     * Reserved word used in CATS DSL files to specify the http method to be run.
     */
    public static final String HTTP_METHOD = "httpMethod";
    /**
     * Reserved word used in CATS DSL files to specify the expected HTTP response code when the test case is executed.
     */
    public static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";
    /**
     * Reserved word used in CATS DSL files to specify output variables.
     */
    public static final String OUTPUT = "output";
    /**
     * Reserved word used in CATS DSL files to verify different assertions.
     */
    public static final String VERIFY = "verify";
    /**
     * Reserved word used in CATS DSL files to supply a custom file.
     */
    public static final String STRINGS_FILE = "stringsFile";
    /**
     * Reserved word used in CATS DSL files to specify the target fields.
     */
    public static final String TARGET_FIELDS = "targetFields";
    /**
     * Reserved word used in CATS DSL files to specify the target field types.
     */
    public static final String TARGET_FIELDS_TYPES = "targetFieldTypes";
    /**
     * Reserved word used in CATS DSL files to specify anyOf/oneOf selections.
     */
    public static final String ONE_OF_SELECTION = "oneOfSelection";
    /**
     * Reserved word used in CATS DSL files to specify additional properties fields.
     */
    public static final String ADDITIONAL_PROPERTIES = "additionalProperties.*";
    /**
     * Reserved word used in CATS DSL files to specify the top element from additionalProperties.
     */
    public static final String ELEMENT = "topElement";
    /**
     * Reserved word used in CATS DSL files to specify the map values from additionalProperties.
     */
    public static final String MAP_VALUES = "mapValues";
    /**
     * Reserved word used in CATS DSL files to specify new fields added to the payloads.
     */
    public static final String NEW_FIELD = "catsFuzzyField";
    /**
     * Reserved word used in CATS DSL files to specify HTTP headers as targets.
     */
    public static final String CATS_HEADERS = "http_headers";
    /**
     * Reserved word used in CATS DSL files to specify request body as target.
     */
    public static final String CATS_BODY_FUZZ = "http_body";

    /**
     * Returns a list with all the reserved words used by the SpecialFuzzers.
     */
    public static final List<String> RESERVED_WORDS = List.of(DESCRIPTION, HTTP_METHOD, EXPECTED_RESPONSE_CODE, OUTPUT, VERIFY, STRINGS_FILE, TARGET_FIELDS, ONE_OF_SELECTION,
            ADDITIONAL_PROPERTIES, ELEMENT, MAP_VALUES, TARGET_FIELDS_TYPES);
    /**
     * Represents the name of the header used to indicate the Cats Fuzzy Header in HTTP requests.
     * The Cats Fuzzy Header is used for specifying custom behavior related to fuzzing in Cats tests.
     */
    public static final String CATS_FUZZY_HEADER = "Cats-Fuzzy-Header";

    private CatsDSLWords() {
        //ntd
    }

    /**
     * Checks if the given field was added by CATS.
     *
     * @param key the field name
     * @return true if the field was added by CATS, false otherwise
     */
    public static boolean isExtraField(String key) {
        return NEW_FIELD.equalsIgnoreCase(key);
    }
}
