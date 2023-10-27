package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FuzzingDataTest {

    @Test
    void givenASchema_whenGettingAllFieldsAsASingleSet_thenAllFieldsAreReturned() {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMap());
        FuzzingData data = FuzzingData.builder().reqSchema(baseSchema).requestPropertyTypes(this.getBasePropertiesMap()).build();

        Set<String> allProperties = data.getAllFieldsByHttpMethod();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactly("firstName", "lastName");
    }

    @Test
    void givenASchemaWithSubfields_whenGettingAllFieldsAsASingleSet_thenAllFieldsAreReturned() {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields()).requestPropertyTypes(this.buildRequestPropertyTypes()).reqSchema(baseSchema).build();

        Set<String> allProperties = data.getAllFieldsByHttpMethod();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactlyInAnyOrder("firstName", "address", "address#zipCode", "address#street");
    }

    @Test
    void givenAComposedSchema_whenGettingAllFields_thenAllFieldsAreReturnedIncludingTheAllOfFields() {
        ComposedSchema composedSchema = new ComposedSchema();
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMap());
        composedSchema.allOf(Collections.singletonList(baseSchema));

        FuzzingData data = FuzzingData.builder().reqSchema(composedSchema).requestPropertyTypes(this.getBasePropertiesMap()).build();

        Set<String> allProperties = data.getAllFieldsByHttpMethod();
        Assertions.assertThat(allProperties).contains("firstName", "lastName");
    }

    @Test
    void givenAComposedSchema_whenGettingAllRequiredFields_thenAllFieldsAreReturnedIncludingTheAllOfFields() {
        ComposedSchema composedSchema = new ComposedSchema();
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesRequired());
        composedSchema.allOf(Collections.singletonList(baseSchema));
        baseSchema.setRequired(Collections.singletonList("phone"));
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesRequired()).requestPropertyTypes(this.getBasePropertiesRequired()).reqSchema(composedSchema).build();

        List<String> allProperties = data.getAllRequiredFields();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactly("phone");
    }

    @Test
    void givenAComposedSchema_whenGettingAllRequiredFieldsWithSubfields_thenAllFieldsAreReturnedIncludingTheAllOfFields() {
        ComposedSchema composedSchema = new ComposedSchema();
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        composedSchema.allOf(Collections.singletonList(baseSchema));
        baseSchema.setRequired(Collections.singletonList("firstName"));
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields()).requestPropertyTypes(this.buildRequestPropertyTypes()).reqSchema(composedSchema).build();

        List<String> allProperties = data.getAllRequiredFields();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactlyInAnyOrder("firstName", "address#street", "address#zipCode");
    }

    @ParameterizedTest
    @CsvSource({"number,1", "string,2"})
    void shouldIncludeBasedOnFieldType(String type, int expected) {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields())
                .requestPropertyTypes(this.buildRequestPropertyTypes())
                .reqSchema(baseSchema)
                .includeFieldTypes(List.of(type))
                .build();
        Set<CatsField> catsFields = data.getAllFieldsAsCatsFields();

        Assertions.assertThat(catsFields).hasSize(expected);
    }

    @ParameterizedTest
    @CsvSource({"number,3", "string,2"})
    void shouldExcludeBasedOnFieldType(String type, int expected) {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields())
                .requestPropertyTypes(this.buildRequestPropertyTypes())
                .reqSchema(baseSchema)
                .skipFieldTypes(List.of(type))
                .build();
        Set<CatsField> catsFields = data.getAllFieldsAsCatsFields();

        Assertions.assertThat(catsFields).hasSize(expected);
    }

    @ParameterizedTest
    @CsvSource({"street,2", "cats,0"})
    void shouldIncludeBasedOnFieldFormat(String format, int expected) {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields())
                .requestPropertyTypes(this.buildRequestPropertyTypes())
                .reqSchema(baseSchema)
                .includeFieldFormats(List.of(format))
                .build();
        Set<CatsField> catsFields = data.getAllFieldsAsCatsFields();

        Assertions.assertThat(catsFields).hasSize(expected);
    }

    @ParameterizedTest
    @CsvSource({"street,2", "cats,4"})
    void shouldExcludeBasedOnFieldFormat(String format, int expected) {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields())
                .requestPropertyTypes(this.buildRequestPropertyTypes())
                .reqSchema(baseSchema)
                .skipFieldFormats(List.of(format))
                .build();
        Set<CatsField> catsFields = data.getAllFieldsAsCatsFields();

        Assertions.assertThat(catsFields).hasSize(expected);
    }

    @Test
    void shouldGetPowerSet() {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields()).requestPropertyTypes(this.buildRequestPropertyTypes()).reqSchema(baseSchema).build();

        Set<Set<String>> setOfFields = data.getAllFields(FuzzingData.SetFuzzingStrategy.POWERSET, 3);
        Assertions.assertThat(setOfFields).hasSize(15);
    }

    @ParameterizedTest
    @CsvSource({"1,4", "2,10", "3,14", "4,15"})
    void shouldGetBasedOnSize(int maxSizeToRemove, int expected) {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().schemaMap(getBasePropertiesMapWithSubfields()).requestPropertyTypes(this.buildRequestPropertyTypes()).reqSchema(baseSchema).build();

        Set<Set<String>> setOfFields = data.getAllFields(FuzzingData.SetFuzzingStrategy.SIZE, maxSizeToRemove);
        Assertions.assertThat(setOfFields).hasSize(expected);
    }

    public Map<String, Schema> getBasePropertiesRequired() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("address", new StringSchema());
        schemaMap.put("phone", new StringSchema());

        return schemaMap;
    }

    public Map<String, Schema> buildRequestPropertyTypes() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("firstName", new StringSchema().format("street"));
        schemaMap.put("lastName", new StringSchema());
        schemaMap.put("address#street", new StringSchema().format("street"));
        schemaMap.put("address#zipCode", new NumberSchema());
        schemaMap.put("address", new ObjectSchema());

        return schemaMap;
    }

    public Map<String, Schema> getBasePropertiesMapWithSubfields() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("firstName", new StringSchema().format("street"));

        ObjectSchema objectSchema = new ObjectSchema();
        Map<String, Schema> addressMap = new HashMap<>();
        addressMap.put("street", new StringSchema().format("street"));
        addressMap.put("zipCode", new NumberSchema());
        objectSchema.setProperties(addressMap);
        objectSchema.setRequired(Arrays.asList("zipCode", "street"));
        objectSchema.set$ref("test/address");
        schemaMap.put("address", objectSchema);

        return schemaMap;
    }


    public Map<String, Schema> getBasePropertiesMap() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("firstName", new StringSchema());
        schemaMap.put("lastName", new StringSchema());

        return schemaMap;
    }
}
