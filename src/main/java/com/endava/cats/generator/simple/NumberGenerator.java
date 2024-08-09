package com.endava.cats.generator.simple;

import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utility class to generate random numbers.
 */
public class NumberGenerator {
    /**
     * The most negative BigDecimal value representable.
     */
    public static final BigDecimal MOST_NEGATIVE_DECIMAL = new BigDecimal("-999999999999999999999999999999999999999999.99999999999").multiply(BigDecimal.valueOf(Double.MAX_VALUE));
    /**
     * The most positive BigDecimal value representable.
     */
    public static final BigDecimal MOST_POSITIVE_DECIMAL = new BigDecimal("999999999999999999999999999999999999999999.99999999999").multiply(BigDecimal.valueOf(Double.MAX_VALUE));
    /**
     * The most positive BigInteger value representable.
     */
    public static final BigInteger MOST_POSITIVE_INTEGER = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Long.MAX_VALUE));
    /**
     * The most negative BigInteger value representable.
     */
    public static final BigInteger MOST_NEGATIVE_INTEGER = BigInteger.valueOf(Long.MIN_VALUE).add(BigInteger.valueOf(Long.MIN_VALUE));
    /**
     * A constant representing 10,000.
     */
    public static final int TEN_THOUSANDS = 10000;
    /**
     * A constant representing the string "9876".
     */
    public static final String NINE_EIGHT_SEVEN_SIX = "9876";
    /**
     * A constant representing the BigDecimal value 45,555,3333.543543543.
     */
    public static final BigDecimal DECIMAL_CONSTANT = new BigDecimal("455553333.543543543");

    private NumberGenerator() {
        //ntd
    }

    /**
     * Generates a new random long number between min and max boundaries
     *
     * @param min the left boundary
     * @param max the right boundary
     * @return a random long matching the given boundaries
     */
    public static long generateRandomLong(long min, long max) {
        return min + CatsUtil.random().nextLong(max - min);
    }

    /**
     * Generates a left boundary integer value lower than schema's min value.
     *
     * @param schema the OpenAPI schema
     * @return a integer lower than schema's min value
     */
    public static Number generateLeftBoundaryIntegerValue(Schema schema) {
        return generateLeftBoundaryIntegerValue(schema, TEN_THOUSANDS);
    }

    /**
     * Generates a left boundary decimal value lower than schema's min value.
     *
     * @param schema the OpenAPI schema
     * @return a decimal lower than schema's min value
     */
    public static Number generateLeftBoundaryDecimalValue(Schema schema) {
        return generateLeftBoundaryValue(schema, DECIMAL_CONSTANT);
    }

    /**
     * Generates a right boundary decimal value higher than schema's max value.
     *
     * @param schema the OpenAPI schema
     * @return a decimal higher than schema's max value
     */
    public static Number generateRightBoundaryDecimalValue(Schema schema) {
        return generateRightBoundaryValue(schema, DECIMAL_CONSTANT);
    }

    /**
     * Generates a right boundary integer value higher than schema's max value.
     *
     * @param schema the OpenAPI schema
     * @return an integer higher than schema's max value
     */
    public static Number generateRightBoundaryIntegerValue(Schema schema) {
        return generateRightBoundaryIntegerValue(schema, TEN_THOUSANDS);
    }

    private static Number generateRightBoundaryIntegerValue(Schema schema, int toAdd) {
        if (schema.getMaximum() != null) {
            return schema.getMaximum().longValue() + toAdd;
        }
        if (CatsModelUtils.isShortIntegerSchema(schema)) {
            return Integer.MAX_VALUE;
        }
        return Long.MAX_VALUE;
    }

    private static Number generateLeftBoundaryIntegerValue(Schema schema, int toAdd) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().longValue() - toAdd;
        }
        if (CatsModelUtils.isShortIntegerSchema(schema)) {
            return Integer.MIN_VALUE;
        }
        return Long.MIN_VALUE;
    }

    private static BigDecimal generateRightBoundaryValue(Schema schema, BigDecimal toAdd) {
        if (schema.getMaximum() != null) {
            return schema.getMaximum().add(toAdd);
        }
        if (CatsModelUtils.isFloatSchema(schema)) {
            return BigDecimal.valueOf(Float.MAX_VALUE).subtract(BigDecimal.valueOf(Float.MIN_VALUE));
        }

        return BigDecimal.valueOf(Double.MAX_VALUE).subtract(BigDecimal.valueOf(Double.MIN_VALUE));
    }

    private static BigDecimal generateLeftBoundaryValue(Schema schema, BigDecimal toSubtract) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().subtract(toSubtract);
        }
        if (CatsModelUtils.isFloatSchema(schema)) {
            return BigDecimal.valueOf(Float.MIN_VALUE).subtract(BigDecimal.valueOf(Float.MAX_VALUE));
        }

        return BigDecimal.valueOf(Double.MIN_VALUE).subtract(BigDecimal.valueOf(Double.MAX_VALUE));
    }

    /**
     * Generates a large negative number based on Schema type.
     *
     * @param schema the OpenAPI schema
     * @return {@code Long.MIN_VALUE} if Schema is integer, {@code NumberGenerator.MOST_NEGATIVE_INTEGER} otherwise
     */
    public static Number getExtremeNegativeIntegerValue(Schema schema) {
        if (schema.getFormat() == null || CatsModelUtils.isShortIntegerSchema(schema)) {
            return Long.MIN_VALUE;
        }
        return MOST_NEGATIVE_INTEGER;
    }

    /**
     * Generates a large positive number based on Schema type.
     *
     * @param schema the OpenAPI schema
     * @return {@code Long.MAX_VALUE} if Schema is integer, {@code NumberGenerator.MOST_POSITIVE_INTEGER} otherwise
     */
    public static Number getExtremePositiveIntegerValue(Schema schema) {
        if (schema.getFormat() == null || CatsModelUtils.isShortIntegerSchema(schema)) {
            return Long.MAX_VALUE;
        }
        return MOST_POSITIVE_INTEGER;
    }

    /**
     * Generates a large positive decimal based on Schema type.
     *
     * @param schema the OpenAPI schema
     * @return {@code Double.MAX_VALUE} if Schema is float, {@code MOST_POSITIVE_DECIMAL} if format is null, {@code 2 * Double.MAX_VALUE} otherwise
     */
    public static Number getExtremePositiveDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return MOST_POSITIVE_DECIMAL;
        } else if (CatsModelUtils.isFloatSchema(schema)) {
            return Double.MAX_VALUE;
        }
        return BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.valueOf(Double.MAX_VALUE));
    }

    /**
     * Generates a large negative decimal based on Schema type.
     *
     * @param schema the OpenAPI schema
     * @return {@code -Double.MAX_VALUE} if Schema is float, {@code MOST_NEGATIVE_DECIMAL} if format is null, {@code 2 * -Double.MAX_VALUE} otherwise
     */
    public static Number getExtremeNegativeDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return MOST_NEGATIVE_DECIMAL;
        } else if (CatsModelUtils.isFloatSchema(schema)) {
            return -Double.MAX_VALUE;
        }
        return BigDecimal.valueOf(-Double.MAX_VALUE).add(BigDecimal.valueOf(-Double.MAX_VALUE));
    }

    /**
     * Generates a random decimal value.
     *
     * @param schema the OpenAPI schema
     * @return a random decimal
     */
    public static Number generateDecimalValue(Schema schema) {
        BigDecimal minimum = BigDecimal.ZERO;

        if (schema.getMinimum() != null) {
            minimum = schema.getMinimum();
        }

        BigDecimal randomBigDecimal = minimum.add(BigDecimal.valueOf(Math.random()));
        return randomBigDecimal.doubleValue();
    }

    /**
     * Generates the "9876" number repeated the number of {@code times}.
     *
     * @param times the number of times to repeat "9876"
     * @return "9876" number repeated the number of {@code times}
     */
    public static String generateVeryLargeInteger(int times) {
        return StringUtils.repeat(NINE_EIGHT_SEVEN_SIX, times);
    }


    /**
     * Generates the "9876" number repeated the number of {@code times} with 40 decimals.
     *
     * @param times the number of times to repeat "9876"
     * @return "9876" number repeated the number of {@code times} with 40 decimals
     */
    public static String generateVeryLargeDecimal(int times) {
        return StringUtils.repeat(NINE_EIGHT_SEVEN_SIX, times) + "." + StringUtils.repeat(NINE_EIGHT_SEVEN_SIX, 10);
    }
}
