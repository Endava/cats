package com.endava.cats.util;

import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class CatsUtilTest {

    @Test
    public void givenAYamlFile_whenParseYamlIsCalled_thenTheYamlFileIsProperlyParsed() throws Exception {
        CatsUtil catsUtil = new CatsUtil();
        Map<String, Map<String, Object>> yaml = catsUtil.parseYaml("src/test/resources/test.yml");

        Assertions.assertThat(yaml.get("all")).isNotNull();
        Assertions.assertThat(yaml.get("all").get("Authorization")).isNotNull();
    }

    @Test
    public void givenASetAndMinSize_whenGettingAllSetsWithMinSize_thenAllSubsetsAreProperlyReturned() {
        CatsUtil catsUtil = new CatsUtil();
        Set<String> data = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<Set<String>> sets = catsUtil.getAllSetsWithMinSize(data, "2");

        Assertions.assertThat(sets).isNotEmpty();
        Assertions.assertThat(sets).containsExactlyInAnyOrder(Collections.singleton("a"), Collections.singleton("b"), Collections.singleton("c"),
                new HashSet<>(Arrays.asList("a", "b")), new HashSet<>(Arrays.asList("a", "c")), new HashSet<>(Arrays.asList("b", "c")));

    }

    @Test
    public void givenAPayloadAndAFuzzingStrategy_whenReplacingTheFuzzedValue_thenThePayloadIsProperlyFuzzed() {
        CatsUtil catsUtil = new CatsUtil();
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("fuzzed");
        String payload = "{'field':'value', 'anotherField':'otherValue'}";
        FuzzingResult result = catsUtil.replaceFieldWithFuzzedValue(payload, "field", strategy);

        Assertions.assertThat(result.getFuzzedValue()).isEqualTo("fuzzed");
        Assertions.assertThat(result.getJson().toString()).contains("fuzzed");
    }

    @Test
    public void givenAPayloadWithPrimitiveAndNonPrimitiveFields_whenCheckingIfPropertiesArePrimitive_thenTheCheckIsProperlyPerformed() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "{'field':'value', 'anotherField':{'subfield': 'otherValue'}}";

        Assertions.assertThat(catsUtil.isPrimitive(payload, "field")).isTrue();
        Assertions.assertThat(catsUtil.isPrimitive(payload, "anotherField")).isFalse();
        Assertions.assertThat(catsUtil.isPrimitive(payload, "anotherField#subfield")).isTrue();
    }

    @Test
    public void givenAnInvalidJson_whenCallingIsValidJson_thenTheMethodReturnsFalse() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "'field':'a";

        Assertions.assertThat(catsUtil.isValidJson(payload)).isFalse();
    }

    @Test
    public void givenAValidJson_whenCallingIsValidJson_thenTheMethodReturnsTrue() {
        CatsUtil catsUtil = new CatsUtil();
        String payload = "{'field':'value', 'anotherField':{'subfield': 'otherValue'}}";

        Assertions.assertThat(catsUtil.isValidJson(payload)).isTrue();
    }
}
