package com.endava.cats.generator.simple;

import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberGenerator {

    public static final BigDecimal MOST_NEGATIVE_DECIMAL = new BigDecimal("-999999999999999999999999999999999999999999.99999999999");
    public static final BigDecimal MOST_POSITIVE_DECIMAL = new BigDecimal("999999999999999999999999999999999999999999.99999999999");
    public static final BigInteger MOST_POSITIVE_INTEGER = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Long.MAX_VALUE));
    public static final BigInteger MOST_NEGATIVE_INTEGER = BigInteger.valueOf(Long.MIN_VALUE).add(BigInteger.valueOf(Long.MIN_VALUE));
    public static final int TEN_THOUSANDS = 10000;

    public static final String NINE_EIGHT_SEVEN_SIX = "9876";
    public static final BigDecimal DECIMAL_CONSTANT = new BigDecimal("455553333.543543543");

    private NumberGenerator() {
        //ntd
    }

    public static Number generateLeftBoundaryIntegerValue(Schema schema) {
        return generateLeftBoundaryValue(schema, new BigDecimal(TEN_THOUSANDS));
    }

    public static Number generateLeftBoundaryDecimalValue(Schema schema) {
        return generateLeftBoundaryValue(schema, DECIMAL_CONSTANT);
    }

    public static Number generateRightBoundaryDecimalValue(Schema schema) {
        return generateRightBoundaryValue(schema, DECIMAL_CONSTANT);
    }

    public static Number generateRightBoundaryIntegerValue(Schema schema) {
        return generateRightBoundaryValue(schema, new BigDecimal(TEN_THOUSANDS));
    }

    private static BigDecimal generateRightBoundaryValue(Schema schema, BigDecimal toAdd) {
        if (schema.getMaximum() != null) {
            return schema.getMaximum().add(toAdd);
        }

        return new BigDecimal(Long.MAX_VALUE).add(BigDecimal.TEN);
    }

    private static BigDecimal generateLeftBoundaryValue(Schema schema, BigDecimal toSubtract) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().subtract(toSubtract);
        }

        return new BigDecimal(Long.MIN_VALUE).subtract(BigDecimal.TEN);
    }

    public static Number getExtremeNegativeIntegerValue(Schema schema) {
        if (schema.getFormat() == null || schema.getFormat().equalsIgnoreCase("int32")) {
            return Long.MIN_VALUE;
        }
        return MOST_NEGATIVE_INTEGER;
    }

    public static Number getExtremePositiveIntegerValue(Schema schema) {
        if (schema.getFormat() == null || schema.getFormat().equalsIgnoreCase("int32")) {
            return Long.MAX_VALUE;
        }
        return MOST_POSITIVE_INTEGER;
    }

    public static Number getExtremePositiveDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return MOST_POSITIVE_DECIMAL;
        } else if (schema.getFormat().equalsIgnoreCase("float")) {
            return Float.MAX_VALUE;
        }
        return Double.MAX_VALUE;
    }

    public static Number getExtremeNegativeDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return MOST_NEGATIVE_DECIMAL;
        } else if (schema.getFormat().equalsIgnoreCase("float")) {
            return -Float.MAX_VALUE;
        }
        return -Double.MAX_VALUE;
    }

    public static Number generateDecimalValue(Schema schema) {
        BigDecimal minimum = BigDecimal.ZERO;

        if (schema.getMinimum() != null) {
            minimum = schema.getMinimum();
        }

        BigDecimal randomBigDecimal = minimum.add(BigDecimal.valueOf(Math.random()));
        return randomBigDecimal.doubleValue();
    }

    public static String generateVeryLargeInteger(int times) {
        return StringUtils.repeat(NINE_EIGHT_SEVEN_SIX, times);
    }

    public static String generateVeryLargeDecimal(int times) {
        return StringUtils.repeat(NINE_EIGHT_SEVEN_SIX, times) + "." + StringUtils.repeat(NINE_EIGHT_SEVEN_SIX, 10);
    }
}
