package com.endava.cats.report;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.model.RequestTarget;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@QuarkusTest
class UnresolvedParametersSummaryTest {

    @Test
    void shouldRenderNothingWhenThereAreNoUnresolvedOperations() {
        Assertions.assertThat(UnresolvedParametersSummary.render(List.of())).isEmpty();
    }

    @Test
    void shouldRenderOperationsParametersProducerAndRefDataSuggestion() {
        RuntimeResourcePool.OperationOutcome producer = new RuntimeResourcePool.OperationOutcome("/customers", HttpMethod.POST,
                3, 0, Map.of(400, 3));
        RuntimeResourcePool.UnresolvedOperation getCustomer = new RuntimeResourcePool.UnresolvedOperation(
                new RuntimeResourcePool.OperationOutcome("/customers/{customerId}", HttpMethod.GET, 4, 0, Map.of(404, 4)),
                List.of(new RuntimeResourcePool.UnresolvedParameter(new RequestTarget(RequestTarget.Location.PATH, "customerId"),
                        RuntimeResourcePool.UnresolvedReason.NO_VALUE_CAPTURED, producer)));
        RuntimeResourcePool.UnresolvedOperation createOrder = new RuntimeResourcePool.UnresolvedOperation(
                new RuntimeResourcePool.OperationOutcome("/orders", HttpMethod.POST, 2, 0, Map.of(422, 2)),
                List.of(new RuntimeResourcePool.UnresolvedParameter(RequestTarget.body("owner#accountId"),
                        RuntimeResourcePool.UnresolvedReason.CAPTURED_VALUES_NOT_FOUND, null)));

        List<String> lines = UnresolvedParametersSummary.render(List.of(getCustomer, createOrder));

        Assertions.assertThat(lines).containsExactly(
                "2 operation(s) never succeeded with unmodified requests and depend on identifiers CATS could not resolve at runtime:",
                "  GET /customers/{customerId} (0/4 successful, responses: 404x4)",
                "    - customerId [path]: no matching value was captured from earlier successful responses; producer POST /customers: 0/3 successful, responses: 400x3",
                "  POST /orders (0/2 successful, responses: 422x2)",
                "    - owner.accountId [body]: values reused from earlier responses returned 404/410",
                "Supply valid values using --refData (or --urlParams for path parameters). Suggested --refData file content:",
                "\"/customers/{customerId}\":",
                "  \"customerId\": \"<valid customerId>\"",
                "\"/orders\":",
                "  \"owner.accountId\": \"<valid accountId>\"");
    }

    @Test
    void shouldLimitTheNumberOfRenderedOperations() {
        List<RuntimeResourcePool.UnresolvedOperation> operations = IntStream.range(0, UnresolvedParametersSummary.MAX_OPERATIONS + 2)
                .mapToObj(index -> new RuntimeResourcePool.UnresolvedOperation(
                        new RuntimeResourcePool.OperationOutcome("/items" + index + "/{id}", HttpMethod.GET, 1, 0, Map.of(404, 1)),
                        List.of(new RuntimeResourcePool.UnresolvedParameter(new RequestTarget(RequestTarget.Location.PATH, "id"),
                                RuntimeResourcePool.UnresolvedReason.NO_VALUE_CAPTURED, null))))
                .toList();

        List<String> lines = UnresolvedParametersSummary.render(operations);

        Assertions.assertThat(lines).contains("  ... and 2 more operation(s)");
        Assertions.assertThat(lines).filteredOn(line -> line.startsWith("  GET ")).hasSize(UnresolvedParametersSummary.MAX_OPERATIONS);
        Assertions.assertThat(lines).filteredOn(line -> line.startsWith("\"/items")).hasSize(UnresolvedParametersSummary.MAX_OPERATIONS + 2);
    }
}
