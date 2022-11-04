package com.endava.cats.generator.simple;

import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberGenerator {

    public static final BigDecimal MOST_NEGATIVE_DECIMAL = new BigDecimal("-999999999999999999999999999999999999999999.99999999999").multiply(BigDecimal.valueOf(Double.MAX_VALUE));
    public static final BigDecimal MOST_POSITIVE_DECIMAL = new BigDecimal("999999999999999999999999999999999999999999.99999999999").multiply(BigDecimal.valueOf(Double.MAX_VALUE));
    public static final BigInteger MOST_POSITIVE_INTEGER = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Long.MAX_VALUE));
    public static final BigInteger MOST_NEGATIVE_INTEGER = BigInteger.valueOf(Long.MIN_VALUE).add(BigInteger.valueOf(Long.MIN_VALUE));
    public static final int TEN_THOUSANDS = 10000;

    public static final String NINE_EIGHT_SEVEN_SIX = "9876";
    public static final BigDecimal DECIMAL_CONSTANT = new BigDecimal("455553333.543543543");
    public static final String INT_32 = "int32";
    public static final String FLOAT = "float";

    private NumberGenerator() {
        //ntd
    }

    public static Number generateLeftBoundaryIntegerValue(Schema schema) {
        return generateLeftBoundaryIntegerValue(schema, TEN_THOUSANDS);
    }

    public static Number generateLeftBoundaryDecimalValue(Schema schema) {
        return generateLeftBoundaryValue(schema, DECIMAL_CONSTANT);
    }

    public static Number generateRightBoundaryDecimalValue(Schema schema) {
        return generateRightBoundaryValue(schema, DECIMAL_CONSTANT);
    }

    public static Number generateRightBoundaryIntegerValue(Schema schema) {
        return generateRightBoundaryIntegerValue(schema, TEN_THOUSANDS);
    }

    private static Number generateRightBoundaryIntegerValue(Schema schema, int toAdd) {
        if (schema.getMaximum() != null) {
            return schema.getMaximum().longValue() + toAdd;
        }
        if (INT_32.equalsIgnoreCase(schema.getFormat())) {
            return Integer.MAX_VALUE;
        }
        return Long.MAX_VALUE;
    }

    private static Number generateLeftBoundaryIntegerValue(Schema schema, int toAdd) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().longValue() - toAdd;
        }
        if (INT_32.equalsIgnoreCase(schema.getFormat())) {
            return Integer.MIN_VALUE;
        }
        return Long.MIN_VALUE;
    }

    private static BigDecimal generateRightBoundaryValue(Schema schema, BigDecimal toAdd) {
        if (schema.getMaximum() != null) {
            return schema.getMaximum().add(toAdd);
        }
        if (FLOAT.equalsIgnoreCase(schema.getFormat())) {
            return BigDecimal.valueOf(Float.MAX_VALUE).subtract(BigDecimal.valueOf(Float.MIN_VALUE));
        }

        return BigDecimal.valueOf(Double.MAX_VALUE).subtract(BigDecimal.valueOf(Double.MIN_VALUE));
    }

    private static BigDecimal generateLeftBoundaryValue(Schema schema, BigDecimal toSubtract) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().subtract(toSubtract);
        }
        if (FLOAT.equalsIgnoreCase(schema.getFormat())) {
            return BigDecimal.valueOf(Float.MIN_VALUE).subtract(BigDecimal.valueOf(Float.MAX_VALUE));
        }

        return BigDecimal.valueOf(Double.MIN_VALUE).subtract(BigDecimal.valueOf(Double.MAX_VALUE));
    }

    public static Number getExtremeNegativeIntegerValue(Schema schema) {
        if (schema.getFormat() == null || schema.getFormat().equalsIgnoreCase(INT_32)) {
            return Long.MIN_VALUE;
        }
        return MOST_NEGATIVE_INTEGER;
    }

    public static Number getExtremePositiveIntegerValue(Schema schema) {
        if (schema.getFormat() == null || schema.getFormat().equalsIgnoreCase(INT_32)) {
            return Long.MAX_VALUE;
        }
        return MOST_POSITIVE_INTEGER;
    }

    public static Number getExtremePositiveDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return MOST_POSITIVE_DECIMAL;
        } else if (schema.getFormat().equalsIgnoreCase(FLOAT)) {
            return Double.MAX_VALUE;
        }
        return BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.valueOf(Double.MAX_VALUE));
    }

    public static Number getExtremeNegativeDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return MOST_NEGATIVE_DECIMAL;
        } else if (schema.getFormat().equalsIgnoreCase(FLOAT)) {
            return -Double.MAX_VALUE;
        }
        return BigDecimal.valueOf(-Double.MAX_VALUE).add(BigDecimal.valueOf(-Double.MAX_VALUE));
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
