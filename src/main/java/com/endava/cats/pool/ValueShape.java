package com.endava.cats.pool;

enum ValueShape {
    BOOLEAN,
    INTEGER_NUMBER,
    DECIMAL_NUMBER,
    UUID_STRING,
    INTEGER_STRING,
    DECIMAL_STRING,
    STRING;

    boolean isString() {
        return this == UUID_STRING || this == INTEGER_STRING || this == DECIMAL_STRING || this == STRING;
    }

    boolean isNumber() {
        return this == INTEGER_NUMBER || this == DECIMAL_NUMBER;
    }

    boolean isNumericString() {
        return this == INTEGER_STRING || this == DECIMAL_STRING;
    }

    boolean isNumeric() {
        return isNumber() || isNumericString();
    }
}
