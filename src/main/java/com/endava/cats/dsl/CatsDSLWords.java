package com.endava.cats.dsl;

import java.util.List;

public abstract class CatsDSLWords {
    public static final String ALL = "all";
    public static final String DESCRIPTION = "description";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";
    public static final String OUTPUT = "output";
    public static final String VERIFY = "verify";
    public static final String STRINGS_FILE = "stringsFile";
    public static final String TARGET_FIELDS = "targetFields";
    public static final String TARGET_FIELDS_TYPES = "targetFieldTypes";
    public static final String ONE_OF_SELECTION = "oneOfSelection";
    public static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    public static final String ELEMENT = "topElement";
    public static final String MAP_VALUES = "mapValues";
    public static final String NEW_FIELD = "catsFuzzyField";

    public static final List<String> RESERVED_WORDS = List.of(DESCRIPTION, HTTP_METHOD, EXPECTED_RESPONSE_CODE, OUTPUT, VERIFY, STRINGS_FILE, TARGET_FIELDS, ONE_OF_SELECTION,
            ADDITIONAL_PROPERTIES, ELEMENT, MAP_VALUES, TARGET_FIELDS_TYPES);

    private CatsDSLWords() {
        //ntd
    }

    public static boolean isExtraField(String key) {
        return NEW_FIELD.equalsIgnoreCase(key);
    }
}
