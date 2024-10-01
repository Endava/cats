package com.endava.cats.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@QuarkusTest
class DepthLimitingSerializerTest {

    private DepthLimitingSerializer serializer;
    private ObjectMapper mapper;
    private JsonFactory jsonFactory;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        int maxDepth = 3;
        serializer = new DepthLimitingSerializer(maxDepth);
        jsonFactory = new JsonFactory();
    }

    private String serialize(Object value, int maxDepth) throws IOException {
        serializer = new DepthLimitingSerializer(maxDepth);
        StringWriter writer = new StringWriter();
        try (JsonGenerator gen = jsonFactory.createGenerator(writer)) {
            serializer.serialize(value, gen, null);
        }
        return writer.toString();
    }


    @Test
    void testSerializeNull() throws IOException {
        String result = serialize(null, 3);
        assertThat(result)
                .as("Serializing null should produce no output.")
                .isEmpty();
    }

    @Test
    void testSerializePrimitiveInteger() throws IOException {
        int value = 42;
        String result = serialize(value, 3);
        assertThat(result)
                .as("Primitive integer should be serialized correctly.")
                .isEqualTo("42");
    }

    @Test
    void testSerializePrimitiveString() throws IOException {
        String value = "Hello, World!";
        String result = serialize(value, 3);
        assertThat(result)
                .as("Primitive string should be serialized correctly.")
                .isEqualTo("\"Hello, World!\"");
    }

    @Test
    void testSerializePrimitiveBoolean() throws IOException {
        boolean value = true;
        String result = serialize(value, 3);
        assertThat(result)
                .as("Primitive boolean should be serialized correctly.")
                .isEqualTo("true");
    }

    @Test
    void testSerializeIntegerArray() throws IOException {
        int[] array = {1, 2, 3, 4, 5};
        String result = serialize(array, 3);
        assertThat(result)
                .as("Integer array should be serialized correctly.")
                .isEqualTo("[1,2,3,4,5]");
    }

    @Test
    void testSerializeObjectArray() throws IOException {
        String[] array = {"apple", "banana", "cherry"};
        String result = serialize(array, 3);
        assertThat(result)
                .as("Object array should be serialized correctly.")
                .isEqualTo("[\"apple\",\"banana\",\"cherry\"]");
    }

    @Test
    void testSerializeNestedArrayExceedingMaxDepth() throws IOException {
        int[][][] array = {
                {
                        {1, 2}, {3, 4}
                },
                {
                        {5, 6}, {7, 8}
                }
        };

        String result = serialize(array, 3);

        assertThat(result)
                .as("Nested arrays exceeding max depth should be serialized as empty arrays.")
                .isEqualTo("[[[],[]],[[],[]]]");
    }

    @Test
    void testSerializeCollectionList() throws IOException {
        List<String> list = Arrays.asList("red", "green", "blue");
        String result = serialize(list, 3);
        assertThat(result)
                .as("List should be serialized correctly.")
                .isEqualTo("[\"red\",\"green\",\"blue\"]");
    }

    @Test
    void testSerializeCollectionSet() throws IOException {
        Set<Integer> set = new LinkedHashSet<>(Arrays.asList(10, 20, 30));
        String result = serialize(set, 3);
        assertThat(result)
                .as("Set should be serialized correctly.")
                .isEqualTo("[10,20,30]");
    }

    @Test
    void testSerializeNestedCollectionExceedingMaxDepth() throws IOException {
        List<List<List<String>>> nestedList = Arrays.asList(
                Arrays.asList(
                        Arrays.asList("a", "b"),
                        Arrays.asList("c", "d")
                ),
                Arrays.asList(
                        Arrays.asList("e", "f"),
                        Arrays.asList("g", "h")
                )
        );
        String result = serialize(nestedList, 3);

        assertThat(result)
                .as("Nested collections exceeding max depth should be serialized as empty arrays.")
                .isEqualTo("[[[],[]],[[],[]]]");
    }

    @Test
    void testSerializeMapStringKeysPrimitiveValues() throws IOException {
        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);
        String result = serialize(map, 3);

        Map<String, Integer> resultMap = mapper.readValue(result, HashMap.class);
        assertThat(resultMap)
                .as("Map with string keys and primitive values should be serialized correctly.")
                .isEqualTo(map);
    }

    @Test
    void testSerializeMapWithObjectValues() throws IOException {
        Map<String, List<Integer>> map = new HashMap<>();
        map.put("primes", Arrays.asList(2, 3, 5, 7));
        map.put("evens", Arrays.asList(2, 4, 6, 8));
        String result = serialize(map, 3);

        Map<String, List<Integer>> resultMap = mapper.readValue(result, HashMap.class);
        assertThat(resultMap)
                .as("Map with object values should be serialized correctly.")
                .isEqualTo(map);
    }

    @Test
    void testSerializeMapWithNullKeysAndValues() throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put("validKey", "validValue");
        map.put(null, "valueWithNullKey");
        map.put("keyWithNullValue", null);
        String result = serialize(map, 3);

        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("validKey", "validValue");
        Map<String, String> resultMap = mapper.readValue(result, HashMap.class);
        assertThat(resultMap)
                .as("Map entries with null keys or values should be omitted.")
                .isEqualTo(expectedMap);
    }

    @Test
    void testSerializePrimitiveWithMaxDepthZero() throws IOException {
        int value = 100;
        String result = serialize(value, 0);
        assertThat(result)
                .as("Serialization should not occur when maxDepth is zero.")
                .isEmpty();
    }

    @Test
    void testSerializeComplexNestedStructureWithinMaxDepth() throws IOException {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("level1", Arrays.asList(
                Map.of("level2Key1", "value1"),
                Map.of("level2Key2", Arrays.asList("value2", "value3"))
        ));
        nestedMap.put("level1Key2", "value4");
        String result = serialize(nestedMap, 5);
        String expectedJson = "{\"level1\":[{\"level2Key1\":\"value1\"},{\"level2Key2\":[\"value2\",\"value3\"]}],\"level1Key2\":\"value4\"}";
        assertThat(mapper.readTree(result))
                .as("Complex nested structure within maxDepth should be serialized correctly.")
                .isEqualTo(mapper.readTree(expectedJson));
    }

    @Test
    void testSerializeComplexNestedStructureExceedingMaxDepth() throws IOException {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("level1", Arrays.asList(
                Map.of("level2Key1", Arrays.asList("value1", "value2")),
                Map.of("level2Key2", Arrays.asList("value3", "value4"))
        ));
        String result = serialize(nestedMap, 3);
        String expectedJson = "{\"level1\":[{},{}]}";
        assertThat(mapper.readTree(result))
                .as("Complex nested structure exceeding maxDepth should serialize partial data.")
                .isEqualTo(mapper.readTree(expectedJson));
    }

    @Test
    void testSerializeEmptyArray() throws IOException {
        String[] emptyArray = {};
        String result = serialize(emptyArray, 3);
        assertThat(result)
                .as("Empty array should be serialized as an empty JSON array.")
                .isEqualTo("[]");
    }

    @Test
    void testSerializeEmptyCollection() throws IOException {
        List<String> emptyList = new ArrayList<>();
        String result = serialize(emptyList, 3);
        assertThat(result)
                .as("Empty collection should be serialized as an empty JSON array.")
                .isEqualTo("[]");
    }

    @Test
    void testSerializeEmptyMap() throws IOException {
        Map<String, Object> emptyMap = new HashMap<>();
        String result = serialize(emptyMap, 3);
        assertThat(result)
                .as("Empty map should be serialized as an empty JSON object.")
                .isEqualTo("{}");
    }

    @Test
    void testSerializeMapWithNonStringKeys() throws IOException {
        Map<Object, Object> map = new HashMap<>();
        map.put(1, "one");
        map.put(true, "booleanTrue");
        map.put(3.14, "pi");
        String result = serialize(map, 3);
        // Convert keys to strings manually
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("1", "one");
        expectedMap.put("true", "booleanTrue");
        expectedMap.put("3.14", "pi");
        Map<String, String> resultMap = mapper.readValue(result, HashMap.class);
        assertThat(resultMap)
                .as("Map with non-string keys should serialize keys using toString().")
                .isEqualTo(expectedMap);
    }

    @Test
    void testCurrentDepthManagement() throws IOException {
        String json1 = serialize("test", 3);
        assertThat(json1)
                .as("First serialization should work correctly.")
                .isEqualTo("\"test\"");

        List<Integer> list = Arrays.asList(1, 2, 3);
        String json2 = serialize(list, 3);
        assertThat(json2)
                .as("Second serialization should work correctly.")
                .isEqualTo("[1,2,3]");
    }

    @Test
    void testSerializeObjectWithCircularReferences() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("self", map);

        String result = serialize(map, 3);

        Assertions.assertThat(result).isEqualTo("{\"self\":{\"self\":{}}}");
    }

    @Test
    void testSerializeComplexObject() throws IOException {
        Map<String, Object> complexObject = new HashMap<>();
        complexObject.put("int", 123);
        complexObject.put("string", "test");
        complexObject.put("boolean", true);
        complexObject.put("list", Arrays.asList("a", "b", "c"));
        complexObject.put("map", Map.of("nestedKey", "nestedValue"));

        String result = serialize(complexObject, 3);
        String expectedJson = "{\"int\":123,\"string\":\"test\",\"boolean\":true,\"list\":[\"a\",\"b\",\"c\"],\"map\":{\"nestedKey\":\"nestedValue\"}}";
        assertThat(mapper.readTree(result))
                .as("Complex object should be serialized correctly.")
                .isEqualTo(mapper.readTree(expectedJson));
    }

    @Test
    void testSerializeWithNegativeMaxDepth() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        String result = serialize(map, -1);
        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void testSerializeCollectionWithNullElements() throws IOException {
        List<String> list = Arrays.asList("a", null, "b", null, "c");
        String result = serialize(list, 3);
        assertThat(result)
                .as("Null elements in collections should be skipped.")
                .isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    void testSerializeArrayWithNullElements() throws IOException {
        String[] array = {"x", null, "y", null, "z"};
        String result = serialize(array, 3);
        assertThat(result)
                .as("Null elements in arrays should be skipped.")
                .isEqualTo("[\"x\",\"y\",\"z\"]");
    }

    @Test
    void testSerializeMapWithNestedNullValues() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", null);

        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("nestedKey1", null);
        innerMap.put("nestedKey2", "nestedValue2");
        map.put("key3", innerMap);

        String result = serialize(map, 3);
        String expectedJson = "{\"key1\":\"value1\",\"key3\":{\"nestedKey2\":\"nestedValue2\"}}";
        assertThat(mapper.readTree(result))
                .as("Nested null values in maps should be omitted.")
                .isEqualTo(mapper.readTree(expectedJson));
    }
}
