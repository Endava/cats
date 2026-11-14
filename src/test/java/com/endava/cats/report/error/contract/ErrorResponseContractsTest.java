package com.endava.cats.report.error.contract;

import com.endava.cats.model.ErrorSignature;
import com.endava.cats.model.RequestVocabulary;
import com.endava.cats.report.error.fields.ErrorFieldRole;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class ErrorResponseContractsTest {
    private static final String CONTRACT = """
            openapi: 3.0.3
            info: {title: orders, version: "1"}
            paths:
              /orders:
                post:
                  responses:
                    '201': {description: created}
                    '404':
                      description: missing
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              errorText: {type: string}
                              incidentCode: {type: string, format: uuid}
                    '4XX':
                      description: client error
                      content:
                        application/problem+json:
                          schema: {$ref: '#/components/schemas/Fault'}
                    default:
                      $ref: '#/components/responses/Failure'
              /plain:
                get:
                  responses:
                    '200': {description: ok}
            components:
              responses:
                Failure:
                  description: failure
                  content:
                    application/json:
                      schema: {$ref: '#/components/schemas/Fault'}
              schemas:
                FaultBase:
                  type: object
                  required: [what, trackingRef]
                  properties:
                    trackingRef: {type: string, format: uuid}
                    correlationId: {type: string}
                Fault:
                  allOf:
                    - $ref: '#/components/schemas/FaultBase'
                    - type: object
                      properties:
                        what: {type: string}
                        why: {type: string}
            """;
    private static final String FAULT_A = "{\"what\":\"Name too long\",\"why\":\"max 10\",\"trackingRef\":\"TR-A7F3\",\"correlationId\":\"c-91x\"}";
    private static final String FAULT_B = "{\"what\":\"Name too long\",\"why\":\"max 10\",\"trackingRef\":\"TR-B0C4\",\"correlationId\":\"c-77q\"}";
    private static final OpenAPI OPEN_API = new OpenAPIV3Parser().readContents(CONTRACT, null, null).getOpenAPI();

    @ParameterizedTest
    @CsvSource({"post,/orders,400,true", "POST,/orders,500,true", "post,/orders,404,true", "get,/plain,500,false", "post,/unknown,400,false", "put,/orders,400,false"})
    void shouldDeclareTheErrorBodyOfAKnownOperation(String method, String path, int status, boolean declared) {
        Assertions.assertThat(ErrorResponseContracts.lookup(OPEN_API, method, path, status).topLevelFields().isEmpty()).isEqualTo(!declared);
    }

    @Test
    void shouldNotDeclareAnErrorBodyForResponsesThatAreNotErrors() {
        Assertions.assertThat(ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 200)).isEqualTo(ErrorResponseContract.NONE);
        Assertions.assertThat(ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 399)).isEqualTo(ErrorResponseContract.NONE);
    }

    @Test
    void shouldReturnNoContractWithoutSpecification() {
        Assertions.assertThat(ErrorResponseContracts.lookup(null, "post", "/orders", 400)).isEqualTo(ErrorResponseContract.NONE);
    }

    @Test
    void shouldResolveReferencesAndCompositionAndReadTheRolesFromTheNames() {
        ErrorResponseContract contract = ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 400);

        Assertions.assertThat(contract.topLevelFields()).containsExactlyInAnyOrder("trackingRef", "correlationId", "what", "why");
        Assertions.assertThat(contract.requiredFields()).containsExactlyInAnyOrder("what", "trackingRef");
        Assertions.assertThat(contract.roles()).containsEntry("trackingRef", ErrorFieldRole.VARIABLE)
                .containsEntry("correlationId", ErrorFieldRole.VARIABLE)
                .containsEntry("what", ErrorFieldRole.MESSAGE)
                .containsEntry("why", ErrorFieldRole.MESSAGE);
    }

    @Test
    void shouldPreferTheExactStatusCodeOverItsRangeAndTheRangeOverDefault() {
        Assertions.assertThat(ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 404).topLevelFields())
                .containsExactlyInAnyOrder("errorText", "incidentCode");
        Assertions.assertThat(ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 422).topLevelFields()).contains("what");
        Assertions.assertThat(ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 503).topLevelFields()).contains("what");
    }

    @Test
    void shouldLetTheContractHintWinOverTheFieldNames() {
        ErrorResponseContract contract = ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 400);

        ErrorSignature first = ErrorSignatureMasker.signatureOf(FAULT_A, null, RequestVocabulary.EMPTY, contract);
        ErrorSignature second = ErrorSignatureMasker.signatureOf(FAULT_B, null, RequestVocabulary.EMPTY, contract);

        Assertions.assertThat(String.join(" ", first.tokens())).isEqualTo("Name too long; max NUM");
        Assertions.assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldSplitTheSameBodiesWithoutTheContractBecauseTheNamesSayNothing() {
        ErrorSignature first = ErrorSignatureMasker.signatureOf(FAULT_A, null, RequestVocabulary.EMPTY);
        ErrorSignature second = ErrorSignatureMasker.signatureOf(FAULT_B, null, RequestVocabulary.EMPTY);

        Assertions.assertThat(String.join(" ", first.tokens())).contains("trackingRef: TR-A7F3");
        Assertions.assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldFallBackToTheFieldNamesWhenTheBodyDoesNotMatchTheDeclaredSchema() {
        ErrorResponseContract contract = ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 400);
        String proxyPage = "{\"what\":\"Bad gateway\",\"upstream\":\"orders-1\",\"retries\":\"3\",\"region\":\"eu\"}";

        Assertions.assertThat(contract.matches(JsonParser.parseString(proxyPage).getAsJsonObject())).isFalse();
        Assertions.assertThat(ErrorSignatureMasker.signatureOf(proxyPage, null, RequestVocabulary.EMPTY, contract))
                .isEqualTo(ErrorSignatureMasker.signatureOf(proxyPage, null, RequestVocabulary.EMPTY));
    }

    @Test
    void shouldNotTakeADeclaredVariableFieldForTheErrorTypeWhateverItsName() {
        ErrorResponseContract contract = ErrorResponseContracts.lookup(OPEN_API, "post", "/orders", 404);
        String body = "{\"errorText\":\"Order missing\",\"incidentCode\":\"INC-91x\"}";

        Assertions.assertThat(ErrorSignatureMasker.signatureOf(body, null, RequestVocabulary.EMPTY, contract))
                .isEqualTo(new ErrorSignature("", List.of("Order", "missing"), ""));
        Assertions.assertThat(ErrorSignatureMasker.signatureOf(body, null, RequestVocabulary.EMPTY).errorType()).isEqualTo("INC-91x");
    }
}
