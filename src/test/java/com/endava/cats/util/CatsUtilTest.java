package com.endava.cats.util;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class CatsUtilTest {

    @Test
    void givenASetAndMinSize_whenGettingAllSetsWithMinSize_thenAllSubsetsAreProperlyReturned() {
        Set<String> data = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<Set<String>> sets = FuzzingData.SetFuzzingStrategy.getAllSetsWithMinSize(data, 2);

        Assertions.assertThat(sets)
                .isNotEmpty()
                .containsExactlyInAnyOrder(Collections.singleton("a"), Collections.singleton("b"), Collections.singleton("c"),
                        new HashSet<>(Arrays.asList("a", "b")), new HashSet<>(Arrays.asList("a", "c")), new HashSet<>(Arrays.asList("b", "c")));

    }

    @ParameterizedTest
    @CsvSource(value = {"{\"field\":\"value\", \"anotherField\":\"otherValue\"}|field",
            "{\"field\": 2, \"anotherField\":\"otherValue\"}|field",
            "[{\"field\": 2, \"anotherField\":\"otherValue\"},{\"field\": 2, \"anotherField\":\"otherValue\"}]|field",
            "{\"field\": {\"subField\":\"value\"}, \"anotherField\":\"otherValue\"}|field#subField",
            "{\"field\": [{\"subField\":\"value\"},{\"subField\":\"value\"}], \"anotherField\":\"otherValue\"}|field[*]#subField"}, delimiter = '|')
    void givenAPayloadAndAFuzzingStrategy_whenReplacingTheFuzzedValue_thenThePayloadIsProperlyFuzzed(String json, String path) {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        FuzzingResult result = FuzzingStrategy.replaceField(json, path, strategy);

        Assertions.assertThat(result.fuzzedValue()).isEqualTo("fuzzed");
        Assertions.assertThat(result.json()).contains("fuzzed");
    }

    @Test
    void shouldAddTopElement() {
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Map<String, Object> currentPathValues = Collections.singletonMap("additionalProperties", "{topElement=metadata, mapValues={test1=value1,test2=value2}}");
        String updatedPayload = CatsUtil.setAdditionalPropertiesToPayload(currentPathValues, payload);
        Assertions.assertThat(updatedPayload).contains("metadata").contains("test1");
    }

    @Test
    void shouldNotAddTopElement() {
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Map<String, Object> currentPathValues = Collections.singletonMap("additionalProperties", "{mapValues={test1=value1,test2=value2}}");
        String updatedPayload = CatsUtil.setAdditionalPropertiesToPayload(currentPathValues, payload);
        Assertions.assertThat(updatedPayload).doesNotContain("metadata").contains("test1");
    }

    @ParameterizedTest
    @CsvSource({"test,java.lang.String", "10,java.lang.Integer", "20L,java.lang.Long", "20.34,java.lang.Float"})
    void shouldConvertToAppropriateType(String value, String typeName) throws ClassNotFoundException {
        Class<?> expectedType = Class.forName(typeName);
        Object result = CatsUtil.getAsAppropriateType(value);
        Assertions.assertThat(result).isInstanceOf(expectedType);
    }

    @Test
    void shouldReplaceJsonArray() {
        String json = """
                {"key": [3,4,5]}
                """;

        String result = CatsUtil.justReplaceField(json, "key", "replaced").json();
        Assertions.assertThat(result).contains("replaced").doesNotContain("3", "4", "5");
    }

    @Test
    void shouldReturnFileEmptyWhenFileNull() {
        Assertions.assertThat(CatsUtil.isFileEmpty(null)).isTrue();
    }

    @Test
    void shouldReturnFileEmptyWhenFileEmpty() {
        File file = new File("src/test/resources/empty.yml");
        Assertions.assertThat(CatsUtil.isFileEmpty(file)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true,strYYing", "false,stYYng"})
    void shouldInsertInTheMiddleWithoutReplace(boolean insertWithoutReplace, String toCheck) {
        String finalString = CatsUtil.insertInTheMiddle("string", "YY", insertWithoutReplace);

        Assertions.assertThat(finalString).isEqualTo(toCheck);
    }
}
