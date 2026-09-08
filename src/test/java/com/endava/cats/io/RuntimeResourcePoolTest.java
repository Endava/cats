package com.endava.cats.io;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class RuntimeResourcePoolTest {
    private ProcessingArguments processingArguments;
    private FilesArguments filesArguments;
    private RuntimeResourcePool resourcePool;

    @BeforeEach
    void setup() throws Exception {
        processingArguments = new ProcessingArguments();
        filesArguments = new FilesArguments();
        filesArguments.loadRefData();
        filesArguments.loadQueryParams();
        filesArguments.loadURLParams();
        resourcePool = new RuntimeResourcePool(processingArguments, filesArguments);
    }

    @Test
    void shouldDoNothingWhenRuntimeResourceReuseIsDisabled() {
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).isEqualTo("{\"customerId\":\"generated\"}");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldReusePostResponseIdForLaterPathParameter() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-42");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation -> {
            Assertions.assertThat(correlation.getSourceMethod()).isEqualTo("POST");
            Assertions.assertThat(correlation.getSourcePath()).isEqualTo("/customers");
            Assertions.assertThat(correlation.getSourceLocation()).isEqualTo("response.body.$.id");
            Assertions.assertThat(correlation.getTargetLocation()).isEqualTo("path");
            Assertions.assertThat(correlation.getTargetField()).isEqualTo("customerId");
            Assertions.assertThat(correlation.getValue()).isEqualTo("customer-42");
        });
    }

    @Test
    void shouldReuseNonIdResponseFieldsWhenTheyIdentifyPathResources() {
        enableResourceReuse();
        ServiceData createEntry = postData("/collections/{collectionKey}/entries", "{}");
        resourcePool.observe(createEntry,
                request("POST", "/collections/collection-42/entries", "{}"),
                response(201, "{\"locator\":\"issued-locator\"}"));

        ServiceData getEntry = getData("/collections/{collectionKey}/entries/{locator}",
                "{\"collectionKey\":\"generated-collection\",\"locator\":\"generated-locator\"}");
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getEntry, getEntry.getPayload());

        Assertions.assertThat(result.payload()).contains("collection-42", "issued-locator")
                .doesNotContain("generated-collection", "generated-locator");
        Assertions.assertThat(result.correlations())
                .extracting(correlation -> correlation.getTargetField())
                .containsExactlyInAnyOrder("collectionKey", "locator");
        Assertions.assertThat(result.correlations())
                .filteredOn(correlation -> correlation.getTargetField().equals("locator"))
                .singleElement()
                .satisfies(correlation -> Assertions.assertThat(correlation.getSourceLocation())
                        .isEqualTo("response.body.$.locator"));
    }

    @Test
    void shouldCaptureNonIdFieldsFromBulkCreationResponses() {
        enableResourceReuse();
        ServiceData createBulk = postData("/collections/{collectionKey}/entries/bulk", "[]");
        resourcePool.observe(createBulk,
                request("POST", "/collections/collection-42/entries/bulk", "[]"),
                response(201, "[{\"locator\":\"first-locator\"},{\"locator\":\"second-locator\"}]"));

        ServiceData getEntry = getData("/collections/{collectionKey}/entries/{locator}",
                "{\"collectionKey\":\"generated-collection\",\"locator\":\"generated-locator\"}");
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getEntry, getEntry.getPayload());

        Assertions.assertThat(result.payload()).contains("collection-42", "second-locator")
                .doesNotContain("generated-collection", "generated-locator");
        Assertions.assertThat(result.correlations())
                .extracting(correlation -> correlation.getTargetField())
                .containsExactlyInAnyOrder("collectionKey", "locator");
    }

    @Test
    void shouldAssociateGenericIdsWithResourcesBeforeAnActionPathSegment() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers/batch", "[]"), request("POST", "/customers/batch", "[]"),
                response(201, "[{\"id\":\"customer-from-batch\"}]"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-from-batch").doesNotContain("generated");
    }

    @Test
    void shouldAssociateNestedGenericIdsWithIrregularResourceNames() {
        enableResourceReuse();
        resourcePool.observe(postData("/people", "{}"), request("POST", "/people", "{}"),
                response(201, "{\"data\":{\"id\":\"person-42\"}}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/people/{personId}",
                "{\"personId\":\"generated\"}"), "{\"personId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("person-42").doesNotContain("generated");
    }

    @Test
    void shouldCaptureResourcesFromWrappedBulkCreationResponses() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers/batch", "[]"), request("POST", "/customers/batch", "[]"),
                response(201, "{\"items\":[{\"id\":\"first-customer\"},{\"id\":\"second-customer\"}]}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("second-customer").doesNotContain("generated");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation ->
                Assertions.assertThat(correlation.getSourceLocation()).isEqualTo("response.body.$.items[].id"));
    }

    @Test
    void shouldReuseCollectionGetResponseIds() {
        enableResourceReuse();
        ServiceData collectionGet = getData("/customers", "{}");
        resourcePool.observe(collectionGet, request("GET", "/customers", "{}"),
                response(200, "[{\"id\":\"customer-from-get\"}]"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-from-get");
        Assertions.assertThat(result.correlations()).singleElement()
                .satisfies(correlation -> Assertions.assertThat(correlation.getSourceMethod()).isEqualTo("GET"));
    }

    @Test
    void shouldReuseIdsFromWrappedCollectionGetResponses() {
        enableResourceReuse();
        ServiceData collectionGet = getData("/customers", "{}");
        resourcePool.observe(collectionGet, request("GET", "/customers", "{}"),
                response(200, "{\"items\":[{\"id\":\"wrapped-customer\"}],\"total\":1}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("wrapped-customer");
    }

    @Test
    void shouldReuseIdsFromDeeplyWrappedCollectionGetResponses() {
        enableResourceReuse();
        ServiceData collectionGet = getData("/customers", "{}");
        resourcePool.observe(collectionGet, request("GET", "/customers", "{}"),
                response(200, "{\"data\":{\"items\":[{\"id\":\"deeply-wrapped-customer\"}]}}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("deeply-wrapped-customer");
    }

    @Test
    void shouldIgnoreSingleResourceGetResponses() {
        enableResourceReuse();
        ServiceData itemGet = getData("/customers/{customerId}", "{\"customerId\":\"existing\"}");
        resourcePool.observe(itemGet, request("GET", "/customers/existing", itemGet.getPayload()),
                response(200, "{\"id\":\"existing\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}/orders",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("generated");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldReusePutResponseIds() {
        enableResourceReuse();
        ServiceData put = ServiceData.builder().relativePath("/customers").payload("{}")
                .httpMethod(HttpMethod.PUT).contentType("application/json").build();
        resourcePool.observe(put, request("PUT", "/customers", "{}"),
                response(200, "{\"id\":\"customer-from-put\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-from-put");
    }

    @Test
    void shouldReuseIdsInQueryParameters() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData query = ServiceData.builder().relativePath("/orders")
                .payload("{\"customerId\":\"generated\"}").queryParams(Set.of("customerId"))
                .httpMethod(HttpMethod.GET).contentType("application/json").build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(query, query.getPayload());

        Assertions.assertThat(result.payload()).contains("customer-42");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation ->
                Assertions.assertThat(correlation.getTargetLocation()).isEqualTo("query"));
    }

    @Test
    void shouldUseLocationHeaderWhenResponseBodyDoesNotContainAnIdentifier() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"status\":\"created\"}")
                .headers(List.of(new KeyValuePair<>("Location", "http://localhost/customers/customer-from-location")))
                .build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-from-location");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation ->
                Assertions.assertThat(correlation.getSourceLocation()).isEqualTo("response.header.Location"));
    }

    @Test
    void shouldNotCorrelatePathParametersWhenUrlReplacementIsDisabled() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData data = ServiceData.builder()
                .relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}")
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .replaceUrlParams(false)
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(data, data.getPayload());

        Assertions.assertThat(result.payload()).contains("generated");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldPreferNonFuzzedSuccessfulResourcesFromTheSameOperation() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"clean-customer\"}"));
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/customers").payload("{}")
                .fuzzedField("name").httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"fuzzed-customer\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("clean-customer").doesNotContain("fuzzed-customer");
    }

    @Test
    void shouldPreferCleanResourcesOverAutomaticallyDetectedFuzzedResources() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"clean-customer\"}"));
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/customers")
                .payload("{\"name\":\"mutated\"}").originalPayload("{\"name\":\"generated\"}")
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/customers", fuzzedPost.getPayload()),
                response(201, "{\"id\":\"fuzzed-customer\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("clean-customer").doesNotContain("fuzzed-customer");
    }

    @Test
    void shouldCorrelateParentIdsButNotOwnIdsWhenPostingSubresources() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData orderPost = ServiceData.builder()
                .relativePath("/customers/{customerId}/orders")
                .contractPath("/customers/{customerId}/orders")
                .payload("{\"id\":\"new-order\",\"customerId\":\"generated\"}")
                .pathParamsPayload("{\"customerId\":\"generated\"}")
                .queryParams(Set.of())
                .httpMethod(HttpMethod.POST)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(orderPost, orderPost.getPayload());

        Assertions.assertThat(result.payload()).contains("\"id\":\"new-order\"", "customer-42");
        Assertions.assertThat(result.pathParamsPayload()).contains("customer-42");
        Assertions.assertThat(result.correlations()).extracting(correlation -> correlation.getTargetField())
                .containsExactlyInAnyOrder("customerId", "customerId");
    }

    @Test
    void shouldUseNestedFieldSemanticsAndPreserveTheCreatedResourcesOwnId() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData orderPost = postData("/orders",
                "{\"order\":{\"id\":\"new-order\"},\"customer\":{\"id\":\"generated\"}}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(orderPost, orderPost.getPayload());

        Assertions.assertThat(result.payload()).contains("new-order", "customer-42").doesNotContain("generated");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation -> {
            Assertions.assertThat(correlation.getTargetField()).isEqualTo("customer#id");
            Assertions.assertThat(correlation.getTargetLocation()).isEqualTo("body");
        });
    }

    @Test
    void shouldMatchNestedIdsToTheirQualifiedResponseFields() {
        enableResourceReuse();
        resourcePool.observe(postData("/orders", "{}"), request("POST", "/orders", "{}"),
                response(201, "{\"customerId\":\"customer-42\",\"orderId\":\"order-99\"}"));
        ServiceData invoicePost = postData("/invoices",
                "{\"customer\":{\"id\":\"generated-customer\"},\"order\":{\"id\":\"generated-order\"}}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(invoicePost, invoicePost.getPayload());

        Assertions.assertThat(result.payload()).contains("customer-42", "order-99")
                .doesNotContain("generated-customer", "generated-order");
        Assertions.assertThat(result.correlations()).extracting(correlation -> correlation.getTargetField())
                .containsExactlyInAnyOrder("customer#id", "order#id");
    }

    @Test
    void shouldMergeCorrelationWithPrefixMutation() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzedData = ServiceData.builder()
                .relativePath("/customers/{customerId}")
                .contractPath("/customers/{customerId}")
                .payload("{\"customerId\":\"  fuzzed\"}")
                .queryParams(Set.of())
                .fuzzedField("customerId")
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.payload()).contains("  customer-42");
        Assertions.assertThat(result.correlations()).hasSize(1);
    }

    @Test
    void shouldNotOverrideReplacementMutation() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzedData = ServiceData.builder()
                .relativePath("/customers/{customerId}")
                .contractPath("/customers/{customerId}")
                .payload("{\"customerId\":\"invalid-id\"}")
                .queryParams(Set.of())
                .fuzzedField("customerId")
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.payload()).contains("invalid-id").doesNotContain("customer-42");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldAutomaticallyPreserveIdentifierMutationsFromSimpleExecutors() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzedData = ServiceData.builder()
                .relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"attacker-controlled-id\"}")
                .originalPayload("{\"customerId\":\"original-id\"}")
                .queryParams(Set.of())
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.payload()).contains("attacker-controlled-id").doesNotContain("customer-42");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldAutomaticallyMergePrefixMutationsFromSimpleExecutors() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzedData = ServiceData.builder()
                .relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"  original-id\"}")
                .originalPayload("{\"customerId\":\"original-id\"}")
                .queryParams(Set.of())
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.payload()).contains("  customer-42");
        Assertions.assertThat(result.correlations()).hasSize(1);
    }

    @Test
    void shouldPreserveIdentifierMutationsInAnyArrayElement() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzedData = ServiceData.builder()
                .relativePath("/orders")
                .payload("{\"customers\":[{\"customerId\":\"original-1\"},{\"customerId\":\"mutated\"}]}")
                .originalPayload("{\"customers\":[{\"customerId\":\"original-1\"},{\"customerId\":\"original-2\"}]}")
                .queryParams(Set.of())
                .httpMethod(HttpMethod.PUT)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.payload()).contains("original-1", "mutated").doesNotContain("customer-42");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldLetExplicitReferenceDataWin() {
        enableResourceReuse();
        ReflectionTestUtils.setField(filesArguments, "refData",
                Map.of("/customers/{customerId}", Map.of("customerId", "explicit-customer")));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));

        ServiceData data = getData("/customers/{customerId}", "{\"customerId\":\"explicit-customer\"}");
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(data, data.getPayload());

        Assertions.assertThat(result.payload()).contains("explicit-customer").doesNotContain("customer-42");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldInvalidateResourceAfterSuccessfulDelete() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData delete = ServiceData.builder().relativePath("/customers/{customerId}")
                .httpMethod(HttpMethod.DELETE).contentType("application/json").build();
        resourcePool.observe(delete, request("DELETE", "/customers/customer-42", "{}"), response(204, "{}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("generated");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    private void enableResourceReuse() {
        ReflectionTestUtils.setField(processingArguments, "reuseSuccessfulResources", true);
    }

    private ServiceData postData(String path, String payload) {
        return ServiceData.builder().relativePath(path).contractPath(path).payload(payload).queryParams(Set.of())
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
    }

    private ServiceData getData(String path, String payload) {
        return ServiceData.builder().relativePath(path).contractPath(path).payload(payload).queryParams(Set.of())
                .httpMethod(HttpMethod.GET).contentType("application/json").build();
    }

    private CatsRequest request(String method, String path, String payload) {
        return CatsRequest.builder().httpMethod(method).url("http://localhost" + path).payload(payload).build();
    }

    private CatsResponse response(int code, String body) {
        return CatsResponse.from(code, body, "", 0);
    }
}
