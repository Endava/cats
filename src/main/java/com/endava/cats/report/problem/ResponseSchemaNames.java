package com.endava.cats.report.problem;

import com.endava.cats.report.error.contract.ErrorResponseContracts;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * Names the response schema the contract declares for a response: the {@code $ref} name, {@code Name[]} for an array
 * of a referenced schema, or {@code METHOD path status} for an inline schema (or when there is no contract).
 */
@SuppressWarnings("rawtypes")
public final class ResponseSchemaNames {
    private final OpenAPI openAPI;

    /**
     * Creates a lookup on a contract.
     *
     * @param openAPI the contract of the run, or {@code null} when it is not available
     */
    public ResponseSchemaNames(OpenAPI openAPI) {
        this.openAPI = openAPI;
    }

    String nameOf(String httpMethod, String contractPath, int statusCode) {
        return ErrorResponseContracts.declaredBodySchema(openAPI, httpMethod, contractPath, statusCode)
                .flatMap(ResponseSchemaNames::referencedName)
                .orElseGet(() -> "%s %s %d".formatted(StringUtils.defaultString(httpMethod).toUpperCase(Locale.ROOT), contractPath, statusCode));
    }

    private static Optional<String> referencedName(Schema schema) {
        return simpleRef(schema).or(() -> Optional.ofNullable(schema.getItems()).flatMap(ResponseSchemaNames::simpleRef).map(name -> name + "[]"));
    }

    private static Optional<String> simpleRef(Schema schema) {
        return Optional.ofNullable(schema.get$ref()).map(CatsModelUtils::getSimpleRef);
    }
}
