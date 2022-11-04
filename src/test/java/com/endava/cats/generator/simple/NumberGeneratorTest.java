package com.endava.cats.generator.simple;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

@QuarkusTest
class NumberGeneratorTest {

    @Test
    void givenAnIntegerSchemaWithDefinedMinimum_whenGeneratingALeftBoundaryInteger_thenTheValueIsLowerThanTheMinimum() {
        Schema schema = new IntegerSchema();
        long minimum = 2000;
        schema.setMinimum(BigDecimal.valueOf(minimum));
        Number leftBoundaryValue = NumberGenerator.generateLeftBoundaryIntegerValue(schema);

        Number expected = new BigDecimal(minimum - NumberGenerator.TEN_THOUSANDS);
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected.longValue());
    }

    @Test
    void givenAnIntegerSchemaWithoutDefinedMinimum_whenGeneratingLeftBoundaryInteger_thenTheValueIsDefault() {
        Schema schema = new IntegerSchema();
        schema.setFormat(null);
        Number leftBoundaryValue = NumberGenerator.generateLeftBoundaryIntegerValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void shouldGenerateMinIntegerWhenFormatInt32AndNoMinimum() {
        Schema schema = new IntegerSchema();
        Number leftBoundaryValue = NumberGenerator.generateLeftBoundaryIntegerValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void shouldGenerateMaxIntegerWhenFormatInt32AndNoMaximum() {
        Schema schema = new IntegerSchema();
        Number leftBoundaryValue = NumberGenerator.generateRightBoundaryIntegerValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void givenAnIntegerSchemaWithDefinedMaximum_whenGeneratingARightBoundaryInteger_thenTheValueIsHigherThanTheMaximum() {
        Schema schema = new IntegerSchema();
        long maximum = 2000;
        schema.setMaximum(BigDecimal.valueOf(maximum));
        Number leftBoundaryValue = NumberGenerator.generateRightBoundaryIntegerValue(schema);

        Number expected = new BigDecimal(maximum + NumberGenerator.TEN_THOUSANDS);
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected.longValue());
    }

    @Test
    void givenAnIntegerSchemaWithoutDefinedMaximum_whenGeneratingRightBoundaryInteger_thenTheValueIsDefault() {
        Schema schema = new IntegerSchema();
        schema.setFormat(null);
        Number leftBoundaryValue = NumberGenerator.generateRightBoundaryIntegerValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void givenANumberSchemaWithDefinedMinimum_whenGeneratingALeftBoundaryDecimal_thenTheValueIsLowerThanTheMinimum() {
        Schema schema = new NumberSchema();
        BigDecimal minimum = BigDecimal.valueOf(2000);
        schema.setMinimum(minimum);
        Number leftBoundaryValue = NumberGenerator.generateLeftBoundaryDecimalValue(schema);

        Number expected = minimum.subtract(NumberGenerator.DECIMAL_CONSTANT);
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected);
    }

    @Test
    void givenAnNumberSchemaWithoutDefinedMinimum_whenGeneratingLeftBoundaryDecimal_thenTheValueIsDefault() {
        Schema schema = new NumberSchema();
        Number leftBoundaryValue = NumberGenerator.generateLeftBoundaryDecimalValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(new BigDecimal(Double.MIN_VALUE).subtract(new BigDecimal(Double.MAX_VALUE)));
    }

    @Test
    void givenANumberSchemaWithDefinedMaximum_whenGeneratingARightBoundaryDecimal_thenTheValueIsHigherThanTheMaximum() {
        Schema schema = new NumberSchema();
        schema.setMaximum(BigDecimal.valueOf(2000));
        Number leftBoundaryValue = NumberGenerator.generateRightBoundaryDecimalValue(schema);

        Number expected = BigDecimal.valueOf(2000).add(NumberGenerator.DECIMAL_CONSTANT);
        Assertions.assertThat(leftBoundaryValue).isEqualTo(expected);
    }

    @Test
    void givenANumberSchemaWithoutDefinedMaximum_whenGeneratingRightBoundaryDecimal_thenTheValueIsDefault() {
        Schema schema = new NumberSchema();
        Number leftBoundaryValue = NumberGenerator.generateRightBoundaryDecimalValue(schema);

        Assertions.assertThat(leftBoundaryValue).isEqualTo(new BigDecimal(Double.MAX_VALUE).subtract(new BigDecimal(Double.MIN_VALUE)));
    }

    @Test
    void givenANumberSchemaOfTypeInt32_whenGeneratingAnExtremeNegativeIntegerValue_thenLongMinValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int32");
        Number extremeNegative = NumberGenerator.getExtremeNegativeIntegerValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void givenANumberSchema_whenGeneratingAnExtremeNegativeIntegerValue_thenLongMinValueIsReturned() {
        Schema schema = new NumberSchema();
        Number extremeNegative = NumberGenerator.getExtremeNegativeIntegerValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void givenANumberSchemaOfTypeInt64_whenGeneratingAnExtremeNegativeValue_thenTheMostNegativeConstantIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int64");
        Number extremeNegative = NumberGenerator.getExtremeNegativeIntegerValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(NumberGenerator.MOST_NEGATIVE_INTEGER);
    }

    @Test
    void givenANumberSchemaOfTypeInt32_whenGeneratingAnExtremePositiveIntegerValue_thenLongMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int32");
        Number extremePositive = NumberGenerator.getExtremePositiveIntegerValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void givenANumberSchema_whenGeneratingAnExtremePositiveIntegerValue_thenLongMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        Number extremePositive = NumberGenerator.getExtremePositiveIntegerValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void givenANumberSchemaOfTypeInt64_whenGeneratingAnExtremePositiveValue_thenTheMostPositiveConstantIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("int64");
        Number extremePositive = NumberGenerator.getExtremePositiveIntegerValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(NumberGenerator.MOST_POSITIVE_INTEGER);
    }

    @Test
    void givenANumberSchemaOfTypeFloat_whenGeneratingAnExtremePositiveDecimalValue_thenTheFloatMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("float");
        Number extremePositive = NumberGenerator.getExtremePositiveDecimalValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void givenANumberSchemaWithDefaultFormat_whenGeneratingAnExtremePositiveDecimalValue_thenTheMostPositiveDecimalConstantIsReturned() {
        Schema schema = new NumberSchema();
        Number extremePositive = NumberGenerator.getExtremePositiveDecimalValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(NumberGenerator.MOST_POSITIVE_DECIMAL);
    }

    @Test
    void givenANumberSchemaOfTypeDouble_whenGeneratingAnExtremePositiveDecimalValue_thenDoubleMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("double");
        Number extremePositive = NumberGenerator.getExtremePositiveDecimalValue(schema);

        Assertions.assertThat(extremePositive).isEqualTo(BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.valueOf(Double.MAX_VALUE)));
    }

    @Test
    void givenANumberSchemaOfTypeFloat_whenGeneratingAnExtremeNegativeDecimalValue_thenTheMinusFloatMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("float");
        Number extremeNegative = NumberGenerator.getExtremeNegativeDecimalValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(-Double.MAX_VALUE);
    }

    @Test
    void givenANumberSchemaWithDefaultFormat_whenGeneratingAnExtremeNegativeDecimalValue_thenTheMostNegativeDecimalConstantIsReturned() {
        Schema schema = new NumberSchema();
        Number extremeNegative = NumberGenerator.getExtremeNegativeDecimalValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(NumberGenerator.MOST_NEGATIVE_DECIMAL);
    }

    @Test
    void givenANumberSchemaOfTypeDouble_whenGeneratingAnExtremeNegativeDecimalValue_thenMinusDoubleMaxValueIsReturned() {
        Schema schema = new NumberSchema();
        schema.setFormat("double");
        Number extremeNegative = NumberGenerator.getExtremeNegativeDecimalValue(schema);

        Assertions.assertThat(extremeNegative).isEqualTo(BigDecimal.valueOf(-Double.MAX_VALUE).add(BigDecimal.valueOf(-Double.MAX_VALUE)));
    }

    @Test
    void givenANumberSchema_whenGeneratingARandomDecimalValue_thenAProperValueIsReturned() {
        Schema schema = new NumberSchema();
        BigDecimal minimum = new BigDecimal(10);
        schema.setMinimum(minimum);

        BigDecimal generated = new BigDecimal(NumberGenerator.generateDecimalValue(schema).toString());

        Assertions.assertThat(generated.doubleValue() > minimum.doubleValue() && generated.doubleValue() < minimum.doubleValue() + 1).isTrue();
    }

    @Test
    void shouldGenerateLeftBoundaryWhenFloatAndNoMinimum() {
        Schema schema = new NumberSchema();
        schema.setFormat("float");
        Number generated = NumberGenerator.generateLeftBoundaryDecimalValue(schema);

        Assertions.assertThat(generated).isEqualTo(new BigDecimal(Float.MIN_VALUE).subtract(new BigDecimal(Float.MAX_VALUE)));
    }

    @Test
    void shouldGenerateRightBoundaryWhenFloatAndNoMaximum() {
        Schema schema = new NumberSchema();
        schema.setFormat("float");
        Number generated = NumberGenerator.generateRightBoundaryDecimalValue(schema);

        Assertions.assertThat(generated).isEqualTo(new BigDecimal(Float.MAX_VALUE).subtract(new BigDecimal(Float.MIN_VALUE)));
    }

}
