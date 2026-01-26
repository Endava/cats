package com.endava.cats.util;

/**
 * Iterator that cycles through a set of characters.
 */
public final class CyclingCharIterator {

    private final char[] values;
    private int index = 0;

    /**
     * Creates a new instance.
     *
     * @param values the characters to cycle through
     */
    public CyclingCharIterator(char... values) {
        this.values = values.clone();
    }

    /**
     * Returns the next character in the cycle.
     *
     * @return the next character
     */
    public char next() {
        char c = values[index];
        index = (index + 1) % values.length;
        return c;
    }
}