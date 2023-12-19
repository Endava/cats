package com.endava.cats.util;

import java.util.List;
import java.util.Map;

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

    public static final String CHECK_TRUE = "checkTrue";

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
    public static final String STRINGS_FILE = "stringsFile";
    public static final String TARGET_FIELDS = "targetFields";
    public static final String TARGET_FIELDS_TYPES = "targetFieldTypes";
    public static final String ONE_OF_SELECTION = "oneOfSelection";
    public static final String ADDITIONAL_PROPERTIES = "additionalProperties.*";
    public static final String ELEMENT = "topElement";
    public static final String MAP_VALUES = "mapValues";
    public static final String NEW_FIELD = "catsFuzzyField";
    public static final String CATS_HEADERS = "http_headers";
    public static final String CATS_BODY_FUZZ = "http_body";

    public static final List<String> RESERVED_WORDS = List.of(DESCRIPTION, HTTP_METHOD, EXPECTED_RESPONSE_CODE, OUTPUT, VERIFY, STRINGS_FILE, TARGET_FIELDS, ONE_OF_SELECTION,
            ADDITIONAL_PROPERTIES, ELEMENT, MAP_VALUES, TARGET_FIELDS_TYPES);
    public static final String CATS_FUZZY_HEADER = "Cats-Fuzzy-Header";

    private CatsDSLWords() {
        //ntd
    }

    public static boolean isExtraField(String key) {
        return NEW_FIELD.equalsIgnoreCase(key);
    }
}
