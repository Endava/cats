package com.endava.cats.report.error.contract;

import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the error response schema the OpenAPI contract declares for an operation and a status code, so that error
 * template mining can take the field roles from it ({@link ErrorSchemaFields}). Like the validation of responses, it
 * tries the exact status code, then its range ({@code 4XX}, {@code 5XX}), then {@code default}, and takes the first
 * of them that declares a body schema.
 */
@SuppressWarnings("rawtypes")
public final class ErrorResponseContracts {
    private static final int MIN_ERROR_STATUS = 400;
    private static final Comparator<Map.Entry<String, MediaType>> JSON_FIRST = Comparator
            .comparing(entry -> !entry.getKey().toLowerCase(Locale.ROOT).contains("json"));

    private ErrorResponseContracts() {
    }

    /**
     * Looks up the declared error body of a response.
     *
     * @param openAPI      the contract, may be {@code null}
     * @param httpMethod   the HTTP method, in any case
     * @param contractPath the path as written in the contract, e.g. {@code /pets/{id}}
     * @param statusCode   the response status code
     * @return the declared contract, or {@link ErrorResponseContract#NONE} for a response that is not an error or when
     * the contract declares no body schema
     */
    public static ErrorResponseContract lookup(OpenAPI openAPI, String httpMethod, String contractPath, int statusCode) {
        if (statusCode < MIN_ERROR_STATUS) {
            return ErrorResponseContract.NONE;
        }
        return operation(openAPI, httpMethod, contractPath).map(Operation::getResponses)
                .flatMap(responses -> errorSchema(openAPI, responses, statusCode))
                .map(schema -> new ErrorSchemaFields(componentSchemas(openAPI)).contractOf(schema))
                .orElse(ErrorResponseContract.NONE);
    }

    private static Optional<Operation> operation(OpenAPI openAPI, String httpMethod, String contractPath) {
        return Optional.ofNullable(openAPI).map(OpenAPI::getPaths).map(paths -> paths.get(contractPath))
                .map(PathItem::readOperationsMap)
                .flatMap(operations -> operations.entrySet().stream()
                        .filter(entry -> entry.getKey().name().equalsIgnoreCase(httpMethod))
                        .map(Map.Entry::getValue).findFirst());
    }

    private static Optional<Schema> errorSchema(OpenAPI openAPI, ApiResponses responses, int statusCode) {
        return Stream.of(String.valueOf(statusCode), statusCode / 100 + "XX", "default")
                .map(code -> response(responses, code)).flatMap(Optional::stream)
                .map(response -> resolved(openAPI, response)).map(ErrorResponseContracts::bodySchema)
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<ApiResponse> response(ApiResponses responses, String code) {
        return responses.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(code))
                .map(Map.Entry::getValue).filter(Objects::nonNull).findFirst();
    }

    private static ApiResponse resolved(OpenAPI openAPI, ApiResponse response) {
        return Optional.ofNullable(response.get$ref()).map(CatsModelUtils::getSimpleRef)
                .flatMap(name -> Optional.ofNullable(openAPI.getComponents()).map(Components::getResponses).map(responses -> responses.get(name)))
                .orElse(response);
    }

    private static Optional<Schema> bodySchema(ApiResponse response) {
        return Optional.ofNullable(response.getContent()).stream().flatMap(content -> content.entrySet().stream())
                .sorted(JSON_FIRST).map(entry -> entry.getValue().getSchema()).filter(Objects::nonNull).findFirst();
    }

    private static Map<String, Schema> componentSchemas(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents()).map(Components::getSchemas).orElse(Map.of());
    }
}
