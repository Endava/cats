package com.endava.cats.util;


import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;
import org.cornutum.regexpgen.RandomGen;
import org.cornutum.regexpgen.random.RandomBoundsGen;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility that handles random generation and seed management for deterministic random generation.
 */
public abstract class CatsRandom {

    private static Random random;
    private static Random uuidRandom;
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
        if (random == null) {
            initRandom(0);
        }
        return random;
    }

    /**
     * Returns the current regexp random generator.
     *
     * @return the current regexp random generator.
     */
    public static RandomGen regexpRandomGen() {
        if (regexpRandomGen == null) {
            initRandom(0);
        }
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
        uuidRandom = new Random(storedSeed ^ 0x5DEECE66DL);
        regexpRandomGen = new RandomBoundsGen(random);
    }

    /**
     * Generates a deterministic RFC-4122 version 4 UUID string derived from the configured seed.
     *
     * @return a random v4 UUID string
     */
    public static String uuid() {
        if (uuidRandom == null) {
            initRandom(0);
        }
        long msb = (uuidRandom.nextLong() & ~0x000000000000F000L) | 0x0000000000004000L;
        long lsb = (uuidRandom.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
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
     * Generates a random alphanumeric string with length between minLengthInclusive and maxLengthExclusive.
     *
     * @param minLengthInclusive min length of the string
     * @param maxLengthExclusive max length of the string
     * @return a random alphanumeric string
     */
    public static String alphanumeric(int minLengthInclusive, int maxLengthExclusive) {
        return alphanumeric(randomLength(minLengthInclusive, maxLengthExclusive));
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
     * Generates a random alphabetic string with length between minLengthInclusive and maxLengthExclusive.
     *
     * @param minLengthInclusive min length of the string
     * @param maxLengthExclusive max length of the string
     * @return a random alphabetic string
     */
    public static String alphabetic(int minLengthInclusive, int maxLengthExclusive) {
        return alphabetic(randomLength(minLengthInclusive, maxLengthExclusive));
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
     * Generates a random ascii string with length between minLengthInclusive and maxLengthExclusive.
     *
     * @param minLengthInclusive min length of the string
     * @param maxLengthExclusive max length of the string
     * @return a random ascii string
     */
    public static String ascii(int minLengthInclusive, int maxLengthExclusive) {
        return ascii(randomLength(minLengthInclusive, maxLengthExclusive));
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

    /**
     * Generates a random string with length between minLengthInclusive and maxLengthExclusive.
     *
     * @param minLengthInclusive min length of the string
     * @param maxLengthExclusive max length of the string
     * @return a random string
     */
    public static String next(int minLengthInclusive, int maxLengthExclusive) {
        return next(randomLength(minLengthInclusive, maxLengthExclusive));
    }

    /**
     * Generates a random email address.
     *
     * @return a random email address in the format xxx@cats.io
     */
    public static String email() {
        return alphabetic(10).toLowerCase(Locale.ROOT) + "@cats.io";
    }

    private static int randomLength(int minInclusive, int maxExclusive) {
        if (maxExclusive < minInclusive) {
            throw new IllegalArgumentException("Start value must be smaller or equal to end value.");
        }
        if (minInclusive < 0) {
            throw new IllegalArgumentException("Both range values must be non-negative.");
        }
        if (minInclusive == maxExclusive) {
            return minInclusive;
        }
        return minInclusive + instance().nextInt(maxExclusive - minInclusive);
    }

    private static long randomSeed() {
        return ThreadLocalRandom.current().nextLong();
    }
}
