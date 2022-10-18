package com.endava.cats.util;

import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
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
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        FuzzingResult result = catsUtil.replaceField(json, path, strategy);

        Assertions.assertThat(result.getFuzzedValue()).isEqualTo("fuzzed");
        Assertions.assertThat(result.getJson()).contains("fuzzed");
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

        Assertions.assertThat(result.getFuzzedValue()).asString().isEmpty();
        Assertions.assertThat(result.getJson()).isEmpty();
    }

    @Test
    void shouldReplace() {
        CatsUtil catsUtil = new CatsUtil(new CatsDSLParser());
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
        FuzzingResult result = catsUtil.replaceField(payload, "arrayOfData", FuzzingStrategy.trail().withData("test"));
        Assertions.assertThat(result.getJson()).contains("test").contains("FAoe22OkDDln6qHyqALVI1test").contains("USA");
    }
}
