package com.endava.cats.openapi.handler.index;

/**
 * Represents a position in a specification file, defined by its line and column numbers.
 * This record is used to track the location of elements within the specification.
 */
public record SpecPosition(int line, int column) {
}
