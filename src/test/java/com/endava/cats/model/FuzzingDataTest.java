package com.endava.cats.model;

import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

public class FuzzingDataTest {

    private CatsUtil catsUtil;

    @BeforeEach
    public void setup() {
        catsUtil = Mockito.mock(CatsUtil.class);
    }

    @Test
    public void givenASchema_whenGettingAllFieldsAsASingleSet_thenAllFieldsAreReturned() {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMap());
        FuzzingData data = FuzzingData.builder().catsUtil(catsUtil).reqSchema(baseSchema).build();
        Mockito.doCallRealMethod().when(catsUtil).getAllFields(data);
        Mockito.doCallRealMethod().when(catsUtil).removeOneByOne(Mockito.anySet());

        Set<String> allProperties = data.getAllFieldsAsSingleSet();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactly("firstName", "lastName");
    }

    @Test
    public void givenASchemaWithSubfields_whenGettingAllFieldsAsASingleSet_thenAllFieldsAreReturned() {
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        FuzzingData data = FuzzingData.builder().catsUtil(catsUtil).schemaMap(getBasePropertiesMapWithSubfields()).reqSchema(baseSchema).build();
        Mockito.doCallRealMethod().when(catsUtil).getAllFields(data);
        Mockito.doCallRealMethod().when(catsUtil).removeOneByOne(Mockito.anySet());
        Mockito.doCallRealMethod().when(catsUtil).eliminateStartingCharAndHacks(Mockito.anySet());
        Mockito.doCallRealMethod().when(catsUtil).getDefinitionNameFromRef(Mockito.anyString());

        Set<String> allProperties = data.getAllFieldsAsSingleSet();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactlyInAnyOrder("firstName", "address", "address#zipCode", "address#street");
    }

    @Test
    public void givenAComposedSchema_whenGettingAllFields_thenAllFieldsAreReturnedIncludingTheAllOfFields() {
        ComposedSchema composedSchema = new ComposedSchema();
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMap());
        composedSchema.allOf(Arrays.asList(baseSchema));

        FuzzingData data = FuzzingData.builder().reqSchema(composedSchema).build();

        Map<String, Schema> allProperties = data.getAllProperties();
        Assertions.assertThat(allProperties.get("firstName")).isNotNull();
        Assertions.assertThat(allProperties.get("lastName")).isNotNull();
    }

    @Test
    public void givenAComposedSchema_whenGettingAllRequiredFields_thenAllFieldsAreReturnedIncludingTheAllOfFields() {
        ComposedSchema composedSchema = new ComposedSchema();
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesRequired());
        composedSchema.allOf(Arrays.asList(baseSchema));
        baseSchema.setRequired(Arrays.asList("phone"));
        FuzzingData data = FuzzingData.builder().catsUtil(catsUtil).schemaMap(getBasePropertiesRequired()).reqSchema(composedSchema).build();

        List<String> allProperties = data.getAllRequiredFields();
        Assertions.assertThat(allProperties)
                .isNotEmpty()
                .containsExactly("phone");
    }

    @Test
    public void givenAComposedSchema_whenGettingAllRequiredFieldsWithSubfields_thenAllFieldsAreReturnedIncludingTheAllOfFields() {
        ComposedSchema composedSchema = new ComposedSchema();
        ObjectSchema baseSchema = new ObjectSchema();
        baseSchema.setProperties(this.getBasePropertiesMapWithSubfields());
        composedSchema.allOf(Arrays.asList(baseSchema));
        baseSchema.setRequired(Arrays.asList("firstName"));
        FuzzingData data = FuzzingData.builder().catsUtil(catsUtil).schemaMap(getBasePropertiesMapWithSubfields()).reqSchema(composedSchema).build();
        Mockito.doCallRealMethod().when(catsUtil).getAllFields(data);
        Mockito.doCallRealMethod().when(catsUtil).removeOneByOne(Mockito.anySet());
        Mockito.doCallRealMethod().when(catsUtil).eliminateStartingCharAndHacks(Mockito.anySet());
        Mockito.doCallRealMethod().when(catsUtil).getDefinitionNameFromRef(Mockito.anyString());

        List<String> allProperties = data.getAllRequiredFields();
        Assertions.assertThat(allProperties).isNotEmpty();
        Assertions.assertThat(allProperties).containsExactlyInAnyOrder("firstName", "address#street", "address#zipCode");
    }

    public Map<String, Schema> getBasePropertiesRequired() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("address", new StringSchema());
        schemaMap.put("phone", new StringSchema());

        return schemaMap;
    }

    public Map<String, Schema> getBasePropertiesMapWithSubfields() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("firstName", new StringSchema());

        ObjectSchema objectSchema = new ObjectSchema();
        Map<String, Schema> addressMap = new HashMap<>();
        addressMap.put("street", new StringSchema());
        addressMap.put("zipCode", new StringSchema());
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
