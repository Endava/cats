package com.endava.cats.model;

import java.util.List;

/**
 * The masked, tokenized form of an error response that error template mining works on.
 *
 * @param errorType  the masked error type or title extracted from the response, or empty
 * @param tokens     the masked detail tokens
 * @param stackFrame the first application stack frame, kept verbatim, or empty
 */
public record ErrorSignature(String errorType, List<String> tokens, String stackFrame) {

    public ErrorSignature {
        tokens = List.copyOf(tokens);
    }
}
