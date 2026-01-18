package com.endava.cats.util;


import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;
import org.cornutum.regexpgen.RandomGen;
import org.cornutum.regexpgen.random.RandomBoundsGen;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility that handles random generation and seed management for deterministic random generation.
 */
public abstract class CatsRandom {

    private static Random random;
    private static RandomGen regexpRandomGen;
    @Getter
    private static long storedSeed;


    private CatsRandom() {
        //ntd
    }

    /**
     * Returns the current random generator.
     *
     * @return the current random generator
     */
    public static Random instance() {
        return random;
    }

    /**
     * Returns the current regexp random generator.
     *
     * @return the current regexp random generator.
     */
    public static RandomGen regexpRandomGen() {
        return regexpRandomGen;
    }

    /**
     * Initializes the random generator with a given seed.
     * If the seed is 0, it uses a random seed.
     *
     * @param seed the seed to initialize the random generator
     */
    public static void initRandom(long seed) {
        if (seed == 0) {
            seed = randomSeed();
        }
        storedSeed = seed;
        random = new Random(storedSeed);
        regexpRandomGen = new RandomBoundsGen(random);
    }

    /**
     * Generates a random string of the specified length with alphanumeric characters.
     *
     * @param count the length of the string to generate
     * @return a random string of the specified length with alphanumeric characters
     */
    public static String alphanumeric(int count) {
        return RandomStringUtils.random(count, 0, 0, true, true, null, instance());
    }

    /**
     * Generates a random string of the specified length with alphabetic characters.
     *
     * @param count the length of the string to generate
     * @return a random string of the specified length with alphabetic characters
     */
    public static String alphabetic(int count) {
        return RandomStringUtils.random(count, 0, 0, true, false, null, instance());
    }

    /**
     * Generates a random string of the specified length with numeric characters.
     *
     * @param count the length of the string to generate
     * @return a random string of the specified length with numeric characters
     */
    public static String numeric(int count) {
        return RandomStringUtils.random(count, 0, 0, false, true, null, instance());
    }

    /**
     * Generates a random numeric string of given length.
     *
     * @param minLengthInclusive min length of the string
     * @param maxLengthExclusive max length of the string
     * @return a random numeric string of given length
     */
    public static String numeric(int minLengthInclusive, int maxLengthExclusive) {
        if (maxLengthExclusive < minLengthInclusive) {
            throw new IllegalArgumentException("Start value must be smaller or equal to end value.");
        }
        if (minLengthInclusive < 0) {
            throw new IllegalArgumentException("Both range values must be non-negative.");
        }

        final int len;
        if (minLengthInclusive == maxLengthExclusive) {
            len = minLengthInclusive;
        } else {
            len = minLengthInclusive + instance().nextInt(maxLengthExclusive - minLengthInclusive);
        }
        return numeric(len);
    }

    /**
     * Generates a random string of the specified length with ascii characters.
     *
     * @param count the length of the string to generate
     * @return a random string of the specified length with ascii characters
     */
    public static String ascii(int count) {
        return RandomStringUtils.random(count, 32, 127, false, false, null, instance());
    }

    /**
     * Generates a random string of the specified length with random characters.
     *
     * @param count the length of the string to generate
     * @return a random string of the specified length with random characters
     */
    public static String next(int count) {
        return RandomStringUtils.random(count, 0, 0, false, false, null, instance());
    }


    private static long randomSeed() {
        return ThreadLocalRandom.current().nextLong();
    }
}
