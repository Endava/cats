package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class ContractFuzzerDataUtilForTest {

    public static FuzzingData prepareFuzzingData(String schemaName, String onePropName, HttpMethod method, String... responseCodes) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        ApiResponses apiResponses = new ApiResponses();

        ApiResponse firstApiResponse = new ApiResponse();
        firstApiResponse.set$ref(schemaName);
        for (String responseCode : responseCodes) {
            apiResponses.addApiResponse(responseCode, firstApiResponse);
        }
        ApiResponse secondApiResponse = new ApiResponse();
        Content content = new Content();
        content.addMediaType("application/json", new MediaType().schema(new StringSchema()));
        secondApiResponse.content(content);
        apiResponses.addApiResponse("500", secondApiResponse);
        StringSchema firstName = new StringSchema();
        StringSchema lastName = new StringSchema();
        Map<String, Schema> properties = Map.of("firstName", firstName, onePropName, lastName);
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put(schemaName, new Schema().$ref(schemaName).properties(properties));

        operation.setResponses(apiResponses);
        pathItem.setPost(operation);
        return FuzzingData.builder().path("/pets").method(method).pathItem(pathItem).reqSchemaName(schemaName).requestPropertyTypes(properties)
                .reqSchema(new Schema().$ref(schemaName)).schemaMap(schemaMap).responseCodes(Set.of(responseCodes)).headers(new HashSet<>()).queryParams(new HashSet<>()).build();
    }

    public static FuzzingData prepareFuzzingData(String schemaName, String responseCode) {
        return prepareFuzzingData(schemaName, "lastName", HttpMethod.POST, responseCode);
    }

    public static FuzzingData prepareFuzzingData(String schemaName, String onePropName, String responseCode) {
        return prepareFuzzingData(schemaName, onePropName, HttpMethod.POST, responseCode);
    }
}
