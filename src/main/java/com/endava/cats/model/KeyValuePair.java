package com.endava.cats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * A simple key-value pair class.
 *
 * @param <T> The type of the key.
 * @param <U> The type of the value.
 */
@Getter
@Setter
@EqualsAndHashCode(of = {"key", "value"})
public class KeyValuePair<T, U> {
    private T key;
    private U value;

    /**
     * Constructs a key-value pair with the specified key and value.
     *
     * @param key The key of the key-value pair.
     * @param val The value of the key-value pair.
     */
    public KeyValuePair(T key, U val) {
        this.key = key;
        this.value = val;
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
