package com.endava.cats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(of = {"key", "value"})
public class KeyValuePair<T, U> {
    private T key;
    private U value;

    public KeyValuePair(T key, U val) {
        this.key = key;
        this.value = val;
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
