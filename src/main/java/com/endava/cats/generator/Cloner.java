package com.endava.cats.generator;

import com.endava.cats.model.CatsHeader;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to clone specific objects
 */
public class Cloner {

    private Cloner() {
        //ntd
    }

    /**
     * Creates a new set containing cloned copies of the provided collection of CatsHeader objects.
     *
     * @param items The collection of CatsHeader objects to clone.
     * @return A new set containing cloned copies of the provided CatsHeader objects.
     */
    public static Set<CatsHeader> cloneMe(Collection<CatsHeader> items) {
        return items.stream().map(CatsHeader::copy).collect(Collectors.toCollection(HashSet::new));
    }
}
