package com.endava.cats.strategy;

import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * Helper class that holds methods used to insert data in the middle of existing values.
 */
public final class CommonWithinMethods {

    private CommonWithinMethods() {
        //ntd
    }

    /**
     * Retrieves a list of fuzzing strategies based on the characteristics of the provided field schema.
     *
     * @param fuzzedFieldSchema The schema representing the characteristics of the field to be fuzzed.
     * @param invisibleChars    A list of invisible characters to be used in fuzzing strategies.
     * @param maintainSize      If true, maintains the size of the field during fuzzing; if false, allows size modification.
     * @return A list of {@link FuzzingStrategy} objects representing different fuzzing strategies for the field.
     * It returns {@code FuzzingStrategy.skip()} If the field schema type is not "string" or has a binary/byte format.
     * @throws NullPointerException If 'fuzzedFieldSchema' or 'invisibleChars' is null.
     */
    public static List<FuzzingStrategy> getFuzzingStrategies(Schema<?> fuzzedFieldSchema, List<String> invisibleChars, boolean maintainSize) {
        if (!"string".equalsIgnoreCase(fuzzedFieldSchema.getType())
                || "binary".equalsIgnoreCase(fuzzedFieldSchema.getFormat())
                || "byte".equalsIgnoreCase(fuzzedFieldSchema.getFormat())) {
            return Collections.singletonList(FuzzingStrategy.skip().withData("Field does not match String schema or has binary/byte format"));
        }
        String initialValue = StringGenerator.generateValueBasedOnMinMax(fuzzedFieldSchema);

        /*independent of the supplied strategy, we still maintain sizes for enums*/
        final boolean insertWithoutReplace = !maintainSize || !CollectionUtils.isEmpty(fuzzedFieldSchema.getEnum());

        return invisibleChars
                .stream().map(value -> FuzzingStrategy.replace().withData(insertInTheMiddle(initialValue, value, insertWithoutReplace))).toList();
    }

    /**
     * Inserts a specified string into the middle of another string.
     *
     * @param value                The original string where the insertion is performed.
     * @param whatToInsert         The string to be inserted into the middle of the original string.
     * @param insertWithoutReplace If true, the insertion is done without replacing any characters; if false,
     *                             the insertion replaces a portion of the original string.
     * @return The string resulting from the insertion operation.
     * @throws NullPointerException If 'value' or 'whatToInsert' is null.
     */
    public static String insertInTheMiddle(String value, String whatToInsert, boolean insertWithoutReplace) {
        int position = value.length() / 2;
        int whatToInsertLength = Math.min(value.length(), whatToInsert.length());
        return value.substring(0, position - (insertWithoutReplace ? 0 : whatToInsertLength / 2)) + whatToInsert + value.substring(position + (insertWithoutReplace ? 0 : whatToInsertLength / 2));
    }

    /**
     * Retrieves a fuzzed text based on the maximum size specified by the given schema.
     *
     * @param fuzzedFieldSchema The schema representing the characteristics of the fuzzed field.
     * @param text              The original text to be fuzzed.
     * @return A {@link FuzzingStrategy} representing the fuzzed text based on the maximum size specified by the schema.
     * @throws NullPointerException If 'fuzzedFieldSchema' or 'text' is null.
     */
    public static FuzzingStrategy getTextBasedOnMaxSize(Schema<?> fuzzedFieldSchema, String text) {
        int max = getMax(fuzzedFieldSchema, text);

        return FuzzingStrategy.replace().withData(text.substring(0, max));
    }


    private static int getMax(Schema<?> fuzzedFieldSchema, String text) {
        if (fuzzedFieldSchema.getMaxLength() == null) {
            return text.length();
        }
        return fuzzedFieldSchema.getMaxLength() >= text.length() ? text.length() : fuzzedFieldSchema.getMaxLength();
    }
}
