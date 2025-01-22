package com.endava.cats.command.model;

import org.jetbrains.annotations.NotNull;

/**
 * Data model for Mutators.
 *
 * @param name        the name of the mutator
 * @param description short description of the mutator
 */
public record MutatorEntry(String name, String description) implements Comparable<MutatorEntry> {

    @Override
    public int compareTo(@NotNull MutatorEntry o) {
        return this.name().compareTo(o.name());
    }
}
