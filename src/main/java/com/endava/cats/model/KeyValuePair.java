package com.endava.cats.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class KeyValuePair<T, U> {
    private T key;
    private U value;

    public KeyValuePair(T key, U val) {
        this.key = key;
        this.value = val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyValuePair<?, ?> that = (KeyValuePair<?, ?>) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
