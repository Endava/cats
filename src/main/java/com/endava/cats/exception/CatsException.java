package com.endava.cats.exception;

public class CatsException extends RuntimeException {

    public CatsException(Exception e) {
        super(e);
    }
}
