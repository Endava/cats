package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.HashMap;
import java.util.Map;

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
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put(schemaName, new Schema().$ref(schemaName));

        operation.setResponses(apiResponses);
        pathItem.setPost(operation);
        return FuzzingData.builder().path("/pets").method(method).pathItem(pathItem).reqSchemaName(schemaName)
                .reqSchema(new Schema().$ref(schemaName)).schemaMap(schemaMap).responseCodes(Sets.newHashSet(responseCodes)).headers(Sets.newHashSet()).build();
    }

    public static FuzzingData prepareFuzzingData(String schemaName, String responseCode) {
        return prepareFuzzingData(schemaName, HttpMethod.POST, responseCode);
    }
}
