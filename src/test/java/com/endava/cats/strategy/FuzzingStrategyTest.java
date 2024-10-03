package com.endava.cats.strategy;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.FuzzingResult;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class FuzzingStrategyTest {
    public static final String YY = "YY";

    @Test
    void givenTheFuzzingStrategyClass_whenCallingTheStaticCreateMethods_thenProperInstancesAreReturned() {
        Assertions.assertThat(FuzzingStrategy.trail()).isInstanceOf(TrailFuzzingStrategy.class);
        Assertions.assertThat(FuzzingStrategy.replace()).isInstanceOf(ReplaceFuzzingStrategy.class);
        Assertions.assertThat(FuzzingStrategy.prefix()).isInstanceOf(PrefixFuzzingStrategy.class);
        Assertions.assertThat(FuzzingStrategy.noop()).isInstanceOf(NoopFuzzingStrategy.class);
        Assertions.assertThat(FuzzingStrategy.skip()).isInstanceOf(SkipFuzzingStrategy.class);
    }

    @ParameterizedTest
    @CsvSource({"' '", "'\t'"})
    void givenANullString_whenMergingTheFuzzingWithAnotherString_thenTheReplaceFuzzingStrategyIsApplied(String space) {
        Object result = FuzzingStrategy.mergeFuzzing(String.valueOf(space), "air");

        Assertions.assertThat(result).isEqualTo(String.valueOf(space));
    }

    @ParameterizedTest
    @CsvSource({"' '", "'\t'"})
    void givenASpacedPrefixedString_whenMergingTheFuzzingWithAnotherString_thenThePrefixFuzzingStrategyIsApplied(String space) {
        Object result = FuzzingStrategy.mergeFuzzing(space + space + "test", "air");

        Assertions.assertThat(result).isEqualTo(space + space + "air");
    }

    @ParameterizedTest
    @CsvSource({"' '", "'\t'"})
    void givenASpaceTrailingString_whenMergingTheFuzzingWithAnotherString_thenTheTrailFuzzingStrategyIsApplied(String space) {
        Object result = FuzzingStrategy.mergeFuzzing("test" + space + space, "air");

        Assertions.assertThat(result).isEqualTo("air" + space + space);
    }

    @ParameterizedTest
    @CsvSource({"' '", "'\t'"})
    void givenAnEmptyString_whenMergingTheFuzzingWithAnotherString_thenTheReplaceFuzzingStrategyIsApplied(String space) {
        Object result = FuzzingStrategy.mergeFuzzing("replaced", "air");

        Assertions.assertThat(result).isEqualTo("replaced");
    }

    @Test
    void givenAStringWithNoSpaces_whenMergingTheFuzzingWithAnotherString_thenTheSuppliedValueIsUnchanged() {
        Object result = FuzzingStrategy.mergeFuzzing("test", "air");

        Assertions.assertThat(result).isEqualTo("test");
    }

    @Test
    void givenAFuzzingStrategy_whenSettingAnInnerValueAboveTruncationThreshold_thenTheValueIsProperlyTruncated() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData(StringUtils.repeat("t", 50));

        Assertions.assertThat(strategy.truncatedValue()).isEqualTo(strategy.name() + " with " + StringUtils.repeat("t", 30) + "...");
        Assertions.assertThat(strategy).hasToString(strategy.name() + " with " + StringUtils.repeat("t", 30) + "...");
    }

    @Test
    void givenAFuzzingStrategy_whenSettingAnInnerValueBelowTruncationThreshold_thenTheValueIsNotTruncated() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData(StringUtils.repeat("t", 30));

        Assertions.assertThat(strategy).hasToString(strategy.truncatedValue());
    }

    @Test
    void givenAFuzzingStrategy_whenSettingAnInnerValueWithNull_thenToStringMatchesName() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData(null);

        Assertions.assertThat(strategy).hasToString(strategy.name());
    }

    @Test
    void shouldHaveProperNameForInsert() {
        FuzzingStrategy strategy = FuzzingStrategy.insert().withData(null);

        Assertions.assertThat(strategy).hasToString(strategy.name());
    }

    @Test
    void shouldTruncateValue() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData(StringUtils.repeat("a", 31));

        Assertions.assertThat(strategy.truncatedValue()).contains("REPLACE", "with", StringUtils.repeat("a", 30), "...");
    }

    @Test
    void shouldReturnNameOnlyWhenDataIsNull() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData(null);

        Assertions.assertThat(strategy.truncatedValue()).isEqualTo(strategy.name());
    }

    @ParameterizedTest
    @CsvSource({"'\u2001'", "'\u0001'"})
    void shouldMatchUnicodeWhitespaceAndPrefix(char c) {
        Object result = FuzzingStrategy.mergeFuzzing(c + "test", "air");
        Assertions.assertThat(result).isEqualTo(c + "air");
    }

    @ParameterizedTest
    @CsvSource({"'\u2001'", "'\u0001'"})
    void shouldMatchUnicodeWhitespaceAndTrail(char c) {
        Object result = FuzzingStrategy.mergeFuzzing("test" + c, "air");
        Assertions.assertThat(result).isEqualTo("air" + c);
    }

    @Test
    void shouldInsertInSuppliedValue() {
        Object result = FuzzingStrategy.mergeFuzzing("te\uD83E\uDD76st", "air");
        Assertions.assertThat(result).isEqualTo("a\uD83E\uDD76ir");
    }

    @Test
    void shouldReplaceWhenLargeString() {
        Object result = FuzzingStrategy.mergeFuzzing("caTTTTTTTTts", "air");
        Assertions.assertThat(result).isEqualTo("caTTTTTTTTts");
    }

    @ParameterizedTest
    @CsvSource(value = {"'\u000B\u000B'", "null", "''", "' '"}, nullValues = "null")
    void shouldGetReplaceFuzzStrategy(String value) {
        FuzzingStrategy fuzzingStrategy = FuzzingStrategy.fromValue(value);

        Assertions.assertThat(fuzzingStrategy).isInstanceOf(ReplaceFuzzingStrategy.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"'\u000Bmama'", "'\u1680mama'", "'\u0000mama'", "'\u00ADmama'", "'\u00A0mama'"})
    void shouldGetPrefixFuzzStrategy(String value) {
        FuzzingStrategy fuzzingStrategy = FuzzingStrategy.fromValue(value);

        Assertions.assertThat(fuzzingStrategy).isInstanceOf(PrefixFuzzingStrategy.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"cart", "scats", "bunny"})
    void shouldNotBeLargeString(String value) {
        boolean isLarge = FuzzingStrategy.isLargeString(value);

        Assertions.assertThat(isLarge).isFalse();
    }

    @Test
    void shouldBeLargeString() {
        boolean isLarge = FuzzingStrategy.isLargeString("ca_hey_ts");

        Assertions.assertThat(isLarge).isTrue();
    }

    @Test
    void shouldNotFormatWhenSimpleChars() {
        String formatted = FuzzingStrategy.formatValue("cats");

        Assertions.assertThat(formatted).isEqualTo("cats");
    }

    @Test
    void shouldFormatWhenSpecialChars() {
        String formatted = FuzzingStrategy.formatValue("\u000Bcats");

        Assertions.assertThat(formatted).isEqualTo("\\u000bcats");
    }

    @Test
    void shouldGetNullWhenFormatValueNull() {
        String formatted = FuzzingStrategy.formatValue(null);

        Assertions.assertThat(formatted).isNull();
    }

    @Test
    void shouldMarkText() {
        String result = FuzzingStrategy.markLargeString("test");

        Assertions.assertThat(result).isEqualTo("catestts");
    }


    @ParameterizedTest
    @CsvSource({"number,any", "string,byte", "string,binary"})
    void shouldSkipWhenNotStringSchemaOrBinaryString(String type, String format) {
        Schema schema = new Schema();
        schema.setType(type);
        schema.setFormat(format);

        Map<String, Schema> reqTypes = Map.of("field", schema);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": \"" + StringGenerator.generateValueBasedOnMinMax(schema) + "\"}");


        List<FuzzingStrategy> fuzzingStrategyList = FuzzingStrategy.getFuzzingStrategies(data, "field", List.of("YY"), true);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.get(0).name()).isEqualTo(FuzzingStrategy.skip().name());
    }

    @Test
    void shouldInsertWithoutReplaceWhenNotMaintainSize() {
        StringSchema schema = new StringSchema();
        int length = 10;
        schema.setMinLength(length);
        schema.setMaxLength(length);

        Map<String, Schema> reqTypes = Map.of("field", schema);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": \"" + StringGenerator.generateValueBasedOnMinMax(schema) + "\"}");

        List<FuzzingStrategy> fuzzingStrategyList = FuzzingStrategy.getFuzzingStrategies(data, "field", List.of(YY), false);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.getFirst().getData().toString()).contains(YY).doesNotStartWith(YY).doesNotEndWith(YY).hasSize(length + YY.length());
    }

    @Test
    void shouldInsertWitReplaceWhenMaintainSize() {
        StringSchema schema = new StringSchema();
        int length = 10;
        schema.setMinLength(length);
        schema.setMaxLength(length);

        Map<String, Schema> reqTypes = Map.of("field", schema);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": \"" + StringGenerator.generateValueBasedOnMinMax(schema) + "\"}");


        List<FuzzingStrategy> fuzzingStrategyList = FuzzingStrategy.getFuzzingStrategies(data, "field", List.of(YY), true);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.getFirst().getData().toString()).contains(YY).doesNotStartWith(YY).doesNotEndWith(YY).hasSize(length);
    }

    @Test
    void shouldInsertWithoutReplaceWhenEnums() {
        StringSchema schema = new StringSchema();
        schema.setEnum(List.of("ENUM"));

        Map<String, Schema> reqTypes = Map.of("field", schema);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": \"" + StringGenerator.generateValueBasedOnMinMax(schema) + "\"}");


        List<FuzzingStrategy> fuzzingStrategyList = FuzzingStrategy.getFuzzingStrategies(data, "field", List.of(YY), false);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.getFirst().getData()).isEqualTo("EN" + YY + "UM");
    }

    @Test
    void shouldReturnEmptyFuzzingResultWhenEmptyJson() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        FuzzingResult result = FuzzingStrategy.replaceField("", "test", strategy);

        Assertions.assertThat(result.fuzzedValue()).asString().isEmpty();
        Assertions.assertThat(result.json()).isEmpty();
    }

    @Test
    void shouldReplace() {
        String payload = """
                {
                  "arrayOfData": [
                    "FAoe22OkDDln6qHyqALVI1",
                    "FAoe22OkDDln6qHyqALVI1"
                  ],
                  "country": "USA",
                  "dateTime": "2016-05-24T15:54:14.876Z"
                }
                """;
        FuzzingResult result = FuzzingStrategy.replaceField(payload, "arrayOfData", FuzzingStrategy.trail().withData("test"));
        Assertions.assertThat(result.json()).contains("test").contains("FAoe22OkDDln6qHyqALVI1test").contains("USA");
    }

    @Test
    void shouldReplaceWithArray() {
        String payload = """
                {
                  "arrayWithInteger": [
                    88,99
                  ],
                  "country": "USA"
                }
                """;
        FuzzingResult result = FuzzingStrategy.replaceField(payload, "arrayWithInteger", FuzzingStrategy.replace().withData(List.of(55, 66)));
        Assertions.assertThat(result.json()).contains("55").contains("66").contains("USA").doesNotContain("88").doesNotContain("99");
    }
}