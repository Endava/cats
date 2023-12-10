package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.strategy.CommonWithinMethods;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class CommonWithinMethodsTest {

    public static final String YY = "YY";

    @ParameterizedTest
    @CsvSource({"number,any", "string,byte", "string,binary"})
    void shouldSkipWhenNotStringSchemaOrBinaryString(String type, String format) {
        Schema schema = new Schema();
        schema.setType(type);
        schema.setFormat(format);

        List<FuzzingStrategy> fuzzingStrategyList = CommonWithinMethods.getFuzzingStrategies(schema, List.of("YY"), true);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.get(0).name()).isEqualTo(FuzzingStrategy.skip().name());
    }

    @Test
    void shouldInsertWithoutReplaceWhenNotMaintainSize() {
        StringSchema schema = new StringSchema();
        int length = 10;
        schema.setMinLength(length);
        schema.setMaxLength(length);

        List<FuzzingStrategy> fuzzingStrategyList = CommonWithinMethods.getFuzzingStrategies(schema, List.of(YY), false);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.get(0).getData().toString()).contains(YY).doesNotStartWith(YY).doesNotEndWith(YY).hasSize(length + YY.length());
    }

    @Test
    void shouldInsertWitReplaceWhenMaintainSize() {
        StringSchema schema = new StringSchema();
        int length = 10;
        schema.setMinLength(length);
        schema.setMaxLength(length);

        List<FuzzingStrategy> fuzzingStrategyList = CommonWithinMethods.getFuzzingStrategies(schema, List.of(YY), true);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.get(0).getData().toString()).contains(YY).doesNotStartWith(YY).doesNotEndWith(YY).hasSize(length);
    }

    @Test
    void shouldInsertWithoutReplaceWhenEnums() {
        StringSchema schema = new StringSchema();
        schema.setEnum(List.of("ENUM"));

        List<FuzzingStrategy> fuzzingStrategyList = CommonWithinMethods.getFuzzingStrategies(schema, List.of(YY), false);

        Assertions.assertThat(fuzzingStrategyList).hasSize(1);
        Assertions.assertThat(fuzzingStrategyList.get(0).getData()).isEqualTo("EN" + YY + "UM");
    }

    @ParameterizedTest
    @CsvSource({"true,strYYing", "false,stYYng"})
    void shouldInsertInTheMiddleWithoutReplace(boolean insertWithoutReplace, String toCheck) {
        String finalString = CommonWithinMethods.insertInTheMiddle("string", YY, insertWithoutReplace);

        Assertions.assertThat(finalString).isEqualTo(toCheck);
    }

    @Test
    void shouldReturnFullZalgoTextWhenMaxLengthNull() {
        StringSchema schema = new StringSchema();
        FuzzingStrategy fuzzingStrategy = CommonWithinMethods.getTextBasedOnMaxSize(schema, UnicodeGenerator.getZalgoText());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(UnicodeGenerator.getZalgoText());
    }

    @Test
    void shouldReturnFullZalgoTextWhenMaxLengthGreaterThenZalgoText() {
        StringSchema schema = new StringSchema();
        schema.setMaxLength(1000);
        FuzzingStrategy fuzzingStrategy = CommonWithinMethods.getTextBasedOnMaxSize(schema, UnicodeGenerator.getZalgoText());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(UnicodeGenerator.getZalgoText());
    }

    @Test
    void shouldReturnMaxLengthZalgoText() {
        StringSchema schema = new StringSchema();
        schema.setMaxLength(100);
        FuzzingStrategy fuzzingStrategy = CommonWithinMethods.getTextBasedOnMaxSize(schema, UnicodeGenerator.getZalgoText());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(UnicodeGenerator.getZalgoText().substring(0, 100));
    }
}
