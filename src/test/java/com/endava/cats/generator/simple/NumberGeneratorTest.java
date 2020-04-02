package com.endava.cats.generator.simple;

import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class NumberGeneratorTest {

    @Test
    public void givenAnIntegerSchemaWithDefinedMinimum_whenGeneratingALeftBoundaryInteger_thenTheValueIsLowerThanTheMinimum() {
        Schema schema = new IntegerSchema();
        long minimum = 2000;
        schema.setMinimum(BigDecimal.valueOf(minimum));
        String leftBoundaryValue = NumberGenerator.generateLeftBoundaryIntegerValue(schema);

        String expected = String.valueOf(minimum - NumberGenerator.TEN_THOUSANDS);
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected);
    }

    @Test
    public void givenAnIntegerSchemaWithoutDefinedMinimum_whenGeneratingLeftBoundaryInteger_thenTheValueIsDefault() {
        Schema schema = new IntegerSchema();
        String leftBoundaryValue = NumberGenerator.generateLeftBoundaryIntegerValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(String.valueOf(new BigDecimal(Long.MIN_VALUE).subtract(BigDecimal.TEN)));
    }

    @Test
    public void givenAnIntegerSchemaWithDefinedMaximum_whenGeneratingARightBoundaryInteger_thenTheValueIsHigherThanTheMaximum() {
        Schema schema = new IntegerSchema();
        long maximum = 2000;
        schema.setMaximum(BigDecimal.valueOf(maximum));
        String leftBoundaryValue = NumberGenerator.generateRightBoundaryIntegerValue(schema);

        String expected = String.valueOf(maximum + NumberGenerator.TEN_THOUSANDS);
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected);
    }

    @Test
    public void givenAnIntegerSchemaWithoutDefinedMaximum_whenGeneratingRightBoundaryInteger_thenTheValueIsDefault() {
        Schema schema = new IntegerSchema();
        String leftBoundaryValue = NumberGenerator.generateRightBoundaryIntegerValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(String.valueOf(new BigDecimal(Long.MAX_VALUE).add(BigDecimal.TEN)));
    }

    @Test
    public void givenANumberSchemaWithDefinedMinimum_whenGeneratingALeftBoundaryDecimal_thenTheValueIsLowerThanTheMinimum() {
        Schema schema = new NumberSchema();
        BigDecimal minimum = BigDecimal.valueOf(2000);
        schema.setMinimum(minimum);
        String leftBoundaryValue = NumberGenerator.generateLeftBoundaryDecimalValue(schema);

        String expected = String.valueOf(minimum.subtract(NumberGenerator.DECIMAL_CONSTANT));
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected);
    }

    @Test
    public void givenAnNumberSchemaWithoutDefinedMinimum_whenGeneratingLeftBoundaryDecimal_thenTheValueIsDefault() {
        Schema schema = new NumberSchema();
        String leftBoundaryValue = NumberGenerator.generateLeftBoundaryDecimalValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(String.valueOf(new BigDecimal(Long.MIN_VALUE).subtract(BigDecimal.TEN)));
    }

    @Test
    public void givenANumberSchemaWithDefinedMaximum_whenGeneratingARightBoundaryDecimal_thenTheValueIsHigherThanTheMaximum() {
        Schema schema = new NumberSchema();
        schema.setMaximum(BigDecimal.valueOf(2000));
        String leftBoundaryValue = NumberGenerator.generateRightBoundaryDecimalValue(schema);

        String expected = String.valueOf(BigDecimal.valueOf(2000).add(NumberGenerator.DECIMAL_CONSTANT));
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected);
    }

    @Test
    public void givenANumberSchemaWithoutDefinedMaximum_whenGeneratingRightBoundaryDecimal_thenTheValueIsDefault() {
        Schema schema = new NumberSchema();
        String leftBoundaryValue = NumberGenerator.generateRightBoundaryDecimalValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(String.valueOf(new BigDecimal(Long.MAX_VALUE).add(BigDecimal.TEN)));
    }

    @Test
    public void givenANumberSchemaOfTypeInt32_whenGeneratingAnExtremeNegativeIntegerValue_thenLongMinValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int32");
        String extremeNegative = NumberGenerator.getExtremeNegativeIntegerValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(String.valueOf(Long.MIN_VALUE));
    }

    @Test
    public void givenANumberSchema_whenGeneratingAnExtremeNegativeIntegerValue_thenLongMinValueIsReturned() {
        Schema schema = new NumberSchema();
        String extremeNegative = NumberGenerator.getExtremeNegativeIntegerValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(String.valueOf(Long.MIN_VALUE));
    }

    @Test
    public void givenANumberSchemaOfTypeInt64_whenGeneratingAnExtremeNegativeValue_thenTheMostNegativeConstantIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int64");
        String extremeNegative = NumberGenerator.getExtremeNegativeIntegerValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(String.valueOf(NumberGenerator.MOST_NEGATIVE_INTEGER));
    }

    @Test
    public void givenANumberSchemaOfTypeInt32_whenGeneratingAnExtremePositiveIntegerValue_thenLongMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int32");
        String extremePositive = NumberGenerator.getExtremePositiveIntegerValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(String.valueOf(Long.MAX_VALUE));
    }

    @Test
    public void givenANumberSchema_whenGeneratingAnExtremePositiveIntegerValue_thenLongMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        String extremePositive = NumberGenerator.getExtremePositiveIntegerValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(String.valueOf(Long.MAX_VALUE));
    }

    @Test
    public void givenANumberSchemaOfTypeInt64_whenGeneratingAnExtremePositiveValue_thenTheMostPositiveConstantIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int64");
        String extremePositive = NumberGenerator.getExtremePositiveIntegerValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(String.valueOf(NumberGenerator.MOST_POSITIVE_INTEGER));
    }

    @Test
    public void givenANumberSchemaOfTypeFloat_whenGeneratingAnExtremePositiveDecimalValue_thenTheFloatMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("float");
        String extremePositive = NumberGenerator.getExtremePositiveDecimalValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(String.valueOf(Float.MAX_VALUE));
    }

    @Test
    public void givenANumberSchemaWithDefaultFormat_whenGeneratingAnExtremePositiveDecimalValue_thenTheMostPositiveDecimalConstantIsReturned() {
        Schema schema = new NumberSchema();
        String extremePositive = NumberGenerator.getExtremePositiveDecimalValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(String.valueOf(NumberGenerator.MOST_POSITIVE_DECIMAL));
    }

    @Test
    public void givenANumberSchemaOfTypeDouble_whenGeneratingAnExtremePositiveDecimalValue_thenDoubleMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("double");
        String extremePositive = NumberGenerator.getExtremePositiveDecimalValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(String.valueOf(Double.MAX_VALUE));
    }

    @Test
    public void givenANumberSchemaOfTypeFloat_whenGeneratingAnExtremeNegativeDecimalValue_thenTheMinusFloatMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("float");
        String extremeNegative = NumberGenerator.getExtremeNegativeDecimalValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(String.valueOf(-Float.MAX_VALUE));
    }

    @Test
    public void givenANumberSchemaWithDefaultFormat_whenGeneratingAnExtremeNegativeDecimalValue_thenTheMostNegativeDecimalConstantIsReturned() {
        Schema schema = new NumberSchema();
        String extremeNegative = NumberGenerator.getExtremeNegativeDecimalValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(String.valueOf(NumberGenerator.MOST_NEGATIVE_DECIMAL));
    }

    @Test
    public void givenANumberSchemaOfTypeDouble_whenGeneratingAnExtremeNegativeDecimalValue_thenMinusDoubleMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("double");
        String extremeNegative = NumberGenerator.getExtremeNegativeDecimalValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(String.valueOf(-Double.MAX_VALUE));
    }

    @Test
    public void givenANumberSchema_whenGeneratingARandomDecimalValue_thenAProperValueIsReturned() {
        Schema schema = new NumberSchema();
        BigDecimal minimum = new BigDecimal(10);
        schema.setMinimum(minimum);

        BigDecimal generated = new BigDecimal(NumberGenerator.generateDecimalValue(schema));

        Assertions.assertThat(generated.doubleValue() > minimum.doubleValue() && generated.doubleValue() < minimum.doubleValue() + 1).isTrue();
    }

}
