package com.endava.cats.generator.format;

/**
 * Provide implementation for different formats
 */
public interface FormatGeneratorStrategy {

    /**
     * This method will provide values which seem almost valid for the given format. For example: for an email field something like 'cats@cats.'
     *
     * @return
     */
    String getAlmostValidValue();

    /**
     * This method will provide values which are obviously not valid for the given format. For example: for an email field something like 'cats'
     *
     * @return
     */
    String getTotallyWrongValue();
}
