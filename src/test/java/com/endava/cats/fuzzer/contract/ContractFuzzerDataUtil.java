package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class ContractFuzzerDataUtil {

    public static FuzzingData prepareFuzzingData(String schemaName, HttpMethod method, String... responseCodes) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        ApiResponses apiResponses = new ApiResponses();

        ApiResponse firstApiResponse = new ApiResponse();
        firstApiResponse.set$ref(schemaName);
        for (String responseCode : responseCodes) {
            apiResponses.addApiResponse(responseCode, firstApiResponse);
        }
        StringSchema firstName = new StringSchema();
        StringSchema lastName = new StringSchema();
        Map<String, Schema> properties = Map.of("firstName", firstName, "lastName", lastName);
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put(schemaName, new Schema().$ref(schemaName).properties(properties));

        operation.setResponses(apiResponses);
        pathItem.setPost(operation);
        return FuzzingData.builder().path("/pets").method(method).pathItem(pathItem).reqSchemaName(schemaName).requestPropertyTypes(properties)
                .reqSchema(new Schema().$ref(schemaName)).schemaMap(schemaMap).responseCodes(Set.of(responseCodes)).headers(Set.of()).build();
    }

    public static FuzzingData prepareFuzzingData(String schemaName, String responseCode) {
        return prepareFuzzingData(schemaName, HttpMethod.POST, responseCode);
    }
}
