package com.endava.cats.util;

public class CatsException extends RuntimeException {

    public CatsException(Exception e) {
        super(e);
    }
}
