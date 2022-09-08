package com.endava.cats.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeyValuePair<T, U> {
    private T key;
    private U value;

    public KeyValuePair(T key, U val) {
        this.key = key;
        this.value = val;
    }

    public String toString() {
        return key + "=" + value;
    }
}
