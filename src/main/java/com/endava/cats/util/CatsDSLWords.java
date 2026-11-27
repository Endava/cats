package com.endava.cats.util;

/**
 * Holds constants referring CATS reserved words.
 */
public abstract class CatsDSLWords {
    /**
     * Typically used to flag that user selected all fuzzers or paths.
     */
    public static final String ALL = "all";
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
     * Represents the name of the header used to indicate the Cats Fuzzy Header in HTTP requests.
     * The Cats Fuzzy Header is used for specifying custom behavior related to fuzzing in Cats tests.
     */
    public static final String CATS_FUZZY_HEADER = "Cats-Fuzzy-Header";
    /**
     * Prefix used in reference data or payloads to point to a local file for multipart uploads.
     */
    public static final String CATS_FILE_PREFIX = "cats_file:";

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

    /**
     * Checks if the given value is a CATS {@code cats_file:} local file reference.
     *
     * @param value the candidate value
     * @return true if {@code value} is a string starting with {@link #CATS_FILE_PREFIX}
     */
    public static boolean isFileReference(Object value) {
        return value instanceof String text && text.startsWith(CATS_FILE_PREFIX);
    }
}
