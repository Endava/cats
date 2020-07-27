package com.endava.cats.generator.simple;

import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberGenerator {

    public static final BigDecimal MOST_NEGATIVE_DECIMAL = new BigDecimal("-999999999999999999999999999999999999999999.99999999999");
    public static final BigDecimal MOST_POSITIVE_DECIMAL = new BigDecimal("999999999999999999999999999999999999999999.99999999999");
    public static final BigInteger MOST_POSITIVE_INTEGER = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Long.MAX_VALUE));
    public static final BigInteger MOST_NEGATIVE_INTEGER = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.valueOf(Long.MIN_VALUE));
    public static final int TEN_THOUSANDS = 10000;
    public static final BigDecimal DECIMAL_CONSTANT = new BigDecimal("455553333.543543543");

    private NumberGenerator() {
        //ntd
    }

    public static String generateLeftBoundaryIntegerValue(Schema schema) {
        BigDecimal result = generateLeftBoundaryValue(schema, new BigDecimal(TEN_THOUSANDS));

        return String.valueOf(result.toBigInteger());
    }

    public static String generateLeftBoundaryDecimalValue(Schema schema) {
        BigDecimal result = generateLeftBoundaryValue(schema, DECIMAL_CONSTANT);

        return String.valueOf(result);
    }

    public static String generateRightBoundaryDecimalValue(Schema schema) {
        BigDecimal result = generateRightBoundaryValue(schema, DECIMAL_CONSTANT);
        return String.valueOf(result);
    }

    public static String generateRightBoundaryIntegerValue(Schema schema) {
        BigDecimal result = generateRightBoundaryValue(schema, new BigDecimal(TEN_THOUSANDS));

        return String.valueOf(result.toBigInteger());
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

    public static String getExtremeNegativeIntegerValue(Schema schema) {
        if (schema.getFormat() == null || schema.getFormat().equalsIgnoreCase("int32")) {
            return String.valueOf(Long.MIN_VALUE);
        }
        return String.valueOf(MOST_NEGATIVE_INTEGER);
    }

    public static String getExtremePositiveIntegerValue(Schema schema) {
        if (schema.getFormat() == null || schema.getFormat().equalsIgnoreCase("int32")) {
            return String.valueOf(Long.MAX_VALUE);
        }
        return String.valueOf(MOST_POSITIVE_INTEGER);
    }

    public static String getExtremePositiveDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return String.valueOf(MOST_POSITIVE_DECIMAL);
        } else if (schema.getFormat().equalsIgnoreCase("float")) {
            return String.valueOf(Float.MAX_VALUE);
        }
        return String.valueOf(Double.MAX_VALUE);
    }

    public static String getExtremeNegativeDecimalValue(Schema schema) {
        if (schema.getFormat() == null) {
            return String.valueOf(MOST_NEGATIVE_DECIMAL);
        } else if (schema.getFormat().equalsIgnoreCase("float")) {
            return String.valueOf(-Float.MAX_VALUE);
        }
        return String.valueOf(-Double.MAX_VALUE);
    }

    public static String generateDecimalValue(Schema schema) {
        BigDecimal minimum = BigDecimal.ZERO;

        if (schema.getMinimum() != null) {
            minimum = schema.getMinimum();
        }

        BigDecimal randomBigDecimal = minimum.add(BigDecimal.valueOf(Math.random()));
        return String.valueOf(randomBigDecimal.doubleValue());
    }
}
