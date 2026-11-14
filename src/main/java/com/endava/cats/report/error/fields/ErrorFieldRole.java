package com.endava.cats.report.error.fields;

/**
 * What a field of a JSON error body is for, as far as error template mining is concerned.
 */
public enum ErrorFieldRole {
    /**
     * Identifies the kind of error: an error code, a type or a kind. Part of the template partition key.
     */
    TYPE,
    /**
     * Describes the error in words. Masked and tokenized into the template text.
     */
    MESSAGE,
    /**
     * Holds a per-occurrence value (request id, timestamp, instance URI): never part of the type or the messages.
     * Only declared by an {@link com.endava.cats.report.error.contract.ErrorResponseContract}, never guessed from a name.
     */
    VARIABLE
}
