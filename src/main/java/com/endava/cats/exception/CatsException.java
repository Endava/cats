package com.endava.cats.exception;

/**
 * A custom runtime exception for Cats-related exceptions, extending {@link RuntimeException}.
 */
public class CatsException extends RuntimeException {

    /**
     * Constructs a new {@code CatsException} with the specified cause.
     *
     * @param e the cause of the exception
     */
    public CatsException(Exception e) {
        super(e);
    }

    /**
     * Constructs a new {@code CatsException} with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param e       the cause of the exception
     */
    public CatsException(String message, Exception e) {
        super(message, e);
    }
}
