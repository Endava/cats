package com.endava.cats.util;

import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class CatsUtilTest {

    @Test
    void givenAYamlFile_whenParseYamlIsCalled_thenTheYamlFileIsProperlyParsed() throws Exception {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        Map<String, Map<String, Object>> yaml = catsUtil.parseYaml("src/test/resources/test.yml");

        Assertions.assertThat(yaml.get("all")).isNotNull();
        Assertions.assertThat(yaml.get("all").get("Authorization")).isNotNull();
    }

    @Test
    void givenASetAndMinSize_whenGettingAllSetsWithMinSize_thenAllSubsetsAreProperlyReturned() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        Set<String> data = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<Set<String>> sets = catsUtil.getAllSetsWithMinSize(data, 2);

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
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        FuzzingResult result = catsUtil.replaceField(json, path, strategy);

        Assertions.assertThat(result.getFuzzedValue()).isEqualTo("fuzzed");
        Assertions.assertThat(result.getJson()).contains("fuzzed");
    }

    @Test
    void givenAPayloadWithPrimitiveAndNonPrimitiveFields_whenCheckingIfPropertiesArePrimitive_thenTheCheckIsProperlyPerformed() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Assertions.assertThat(catsUtil.isPrimitive(payload, "field")).isTrue();
        Assertions.assertThat(catsUtil.isPrimitive(payload, "anotherField")).isFalse();
        Assertions.assertThat(catsUtil.isPrimitive(payload, "anotherField#subfield")).isTrue();
    }

    @Test
    void givenAnInvalidJson_whenCallingIsValidJson_thenTheMethodReturnsFalse() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        String payload = "\"field\":\"a";

        Assertions.assertThat(catsUtil.isValidJson(payload)).isFalse();
    }

    @Test
    void givenAValidJson_whenCallingIsValidJson_thenTheMethodReturnsTrue() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Assertions.assertThat(catsUtil.isValidJson(payload)).isTrue();
    }

    @Test
    void shouldAddTopElement() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Map<String, String> currentPathValues = Collections.singletonMap("additionalProperties", "{topElement=metadata, mapValues={test1=value1,test2=value2}}");
        String updatedPayload = catsUtil.setAdditionalPropertiesToPayload(currentPathValues, payload);
        Assertions.assertThat(updatedPayload).contains("metadata").contains("test1");
    }

    @Test
    void shouldNotAddTopElement() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Map<String, String> currentPathValues = Collections.singletonMap("additionalProperties", "{mapValues={test1=value1,test2=value2}}");
        String updatedPayload = catsUtil.setAdditionalPropertiesToPayload(currentPathValues, payload);
        Assertions.assertThat(updatedPayload).doesNotContain("metadata").contains("test1");
    }

    @Test
    void shouldReturnEmptyFuzzingResultWhenEmptyJson() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        FuzzingResult result = catsUtil.replaceField("", "test", strategy);

        Assertions.assertThat(result.getFuzzedValue()).isEmpty();
        Assertions.assertThat(result.getJson()).isEmpty();
    }

    @Test
    void shouldMarkText() {
        String result = CatsUtil.markLargeString("test");

        Assertions.assertThat(result).isEqualTo("catestts");
    }
}
