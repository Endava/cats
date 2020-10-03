package com.endava.cats.util;

import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.google.gson.JsonElement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

class CatsUtilTest {

    @Test
    void givenAYamlFile_whenParseYamlIsCalled_thenTheYamlFileIsProperlyParsed() throws Exception {
        CatsUtil catsUtil = new CatsUtil();
        Map<String, Map<String, Object>> yaml = catsUtil.parseYaml("src/test/resources/test.yml");

        Assertions.assertThat(yaml.get("all")).isNotNull();
        Assertions.assertThat(yaml.get("all").get("Authorization")).isNotNull();
    }

    @Test
    void givenASetAndMinSize_whenGettingAllSetsWithMinSize_thenAllSubsetsAreProperlyReturned() {
        CatsUtil catsUtil = new CatsUtil();
        Set<String> data = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<Set<String>> sets = catsUtil.getAllSetsWithMinSize(data, "2");

        Assertions.assertThat(sets)
                .isNotEmpty()
                .containsExactlyInAnyOrder(Collections.singleton("a"), Collections.singleton("b"), Collections.singleton("c"),
                        new HashSet<>(Arrays.asList("a", "b")), new HashSet<>(Arrays.asList("a", "c")), new HashSet<>(Arrays.asList("b", "c")));

    }

    @Test
    void givenAPayloadAndAFuzzingStrategy_whenReplacingTheFuzzedValue_thenThePayloadIsProperlyFuzzed() {
        CatsUtil catsUtil = new CatsUtil();
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        String payload = "{'field':'value', 'anotherField':'otherValue'}";
        FuzzingResult result = catsUtil.replaceFieldWithFuzzedValue(payload, "field", strategy);

        Assertions.assertThat(result.getFuzzedValue()).isEqualTo("fuzzed");
        Assertions.assertThat(result.getJson().toString()).contains("fuzzed");
    }

    @Test
    void givenAPayloadWithPrimitiveAndNonPrimitiveFields_whenCheckingIfPropertiesArePrimitive_thenTheCheckIsProperlyPerformed() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "{'field':'value', 'anotherField':{'subfield': 'otherValue'}}";

        Assertions.assertThat(catsUtil.isPrimitive(payload, "field")).isTrue();
        Assertions.assertThat(catsUtil.isPrimitive(payload, "anotherField")).isFalse();
        Assertions.assertThat(catsUtil.isPrimitive(payload, "anotherField#subfield")).isTrue();
    }

    @Test
    void givenAnInvalidJson_whenCallingIsValidJson_thenTheMethodReturnsFalse() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "'field':'a";

        Assertions.assertThat(catsUtil.isValidJson(payload)).isFalse();
    }

    @Test
    void givenAValidJson_whenCallingIsValidJson_thenTheMethodReturnsTrue() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "{'field':'value', 'anotherField':{'subfield': 'otherValue'}}";

        Assertions.assertThat(catsUtil.isValidJson(payload)).isTrue();
    }

    @Test
    void shouldAddTopElement() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "{'field':'value', 'anotherField':{'subfield': 'otherValue'}}";

        JsonElement jsonElement = catsUtil.parseAsJsonElement(payload);
        Map<String, String> currentPathValues = Collections.singletonMap("additionalProperties", "{topElement=metadata, mapValues={test1=value1,test2=value2}}");
        catsUtil.setAdditionalPropertiesToPayload(currentPathValues, jsonElement);
        Assertions.assertThat(jsonElement.getAsJsonObject().get("metadata")).isNotNull();
        Assertions.assertThat(jsonElement.getAsJsonObject().get("metadata").getAsJsonObject().get("test1")).isNotNull();
    }

    @Test
    void shouldNotAddTopElement() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "{'field':'value', 'anotherField':{'subfield': 'otherValue'}}";

        JsonElement jsonElement = catsUtil.parseAsJsonElement(payload);
        Map<String, String> currentPathValues = Collections.singletonMap("additionalProperties", "{mapValues={test1=value1,test2=value2}}");
        catsUtil.setAdditionalPropertiesToPayload(currentPathValues, jsonElement);
        Assertions.assertThat(jsonElement.getAsJsonObject().get("metadata")).isNull();
        Assertions.assertThat(jsonElement.getAsJsonObject().get("test1")).isNotNull();
    }
}
