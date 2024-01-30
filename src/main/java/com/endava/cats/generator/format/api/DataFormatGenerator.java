package com.endava.cats.generator.format.api;

/**
 * This interface defines an abstract class for data format generators.
 * A data format generator is responsible for generating data in a specific format.
 */
public interface DataFormatGenerator {
    /**
     * Returns a Predicate to check if the Generator applies to the given format or propertyName
     *
     * @param format       the OpenAPI format
     * @param propertyName the name of tje property
     * @return true if the Generator can be applied to the given input
     */
    boolean appliesTo(String format, String propertyName);

}
