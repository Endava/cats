package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.List;

/**
 * Replaces the target field with arithmetic, index, and overflow boundary numbers
 * (0, -1, off-by-one 32-bit integer overflow, extreme negative floats/doubles, and out-of-range decimals).
 */
@Singleton
public class RandomBoundaryNumbersMutator implements BodyMutator {
    private static final List<Object> BOUNDARY_VALUES = List.of(
            0, -1, 1, -0.0,
            (long) Integer.MAX_VALUE + 1L,
            (long) Integer.MIN_VALUE - 1L,
            -Double.MAX_VALUE,
            -Float.MAX_VALUE,
            65535, 65536, -65536,
            new BigDecimal("1E+309"),
            new BigDecimal("-1E+309"),
            new BigDecimal("1E-324"));

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object value = BOUNDARY_VALUES.get(CatsRandom.instance().nextInt(BOUNDARY_VALUES.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, value).json();
    }

    @Override
    public String description() {
        return "replace field with arithmetic, index, and overflow boundary numbers";
    }
}
