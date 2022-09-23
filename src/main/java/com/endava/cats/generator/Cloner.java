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

    public static Set<CatsHeader> cloneMe(Collection<CatsHeader> items) {
        Set<CatsHeader> clones = new HashSet<>();

        for (CatsHeader t : items) {
            clones.add(t.copy());
        }

        return clones;
    }
}
