package com.endava.cats;

public class StopExecutionException extends RuntimeException {

    public StopExecutionException(String message) {
        super(message);
    }

}
