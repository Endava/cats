package com.endava.cats.generator;

import com.endava.cats.model.CatsHeader;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        Set<CatsHeader> clones = new HashSet<>();

        for (CatsHeader t : items) {
            clones.add(t.copy());
        }

        return clones;
    }
}
