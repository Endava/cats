package com.endava.cats.io.util;

/**
 * Changes an encoded multipart body right before it is sent, after reference data and file references were resolved.
 */
@FunctionalInterface
public interface MultipartMutation {

    /**
     * Applies the mutation.
     *
     * @param encoded the encoded body, carrying the parts that were produced from the payload
     * @return the body to send
     */
    MultipartEncoder.Result apply(MultipartEncoder.Result encoded);
}
