package com.endava.cats.io;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        filesArguments.loadHeaders();
        resourcePool = new RuntimeResourcePool(processingArguments, filesArguments);
    }

    @Test
    void shouldDoNothingWhenRuntimeResourceReuseIsDisabled() {
        ReflectionTestUtils.setField(processingArguments, "reuseSuccessfulResources", false);
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
                .extracting(ResourceCorrelation::getTargetField)
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
                .extracting(ResourceCorrelation::getTargetField)
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
    void shouldReuseCleanSingleResourceGetPathButIgnoreItsResponseBody() {
        enableResourceReuse();
        ServiceData itemGet = getData("/customers/{customerId}", "{\"customerId\":\"existing\"}");
        resourcePool.observe(itemGet, request("GET", "/customers/existing", itemGet.getPayload()),
                response(200, "{\"id\":\"different-response-id\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}/orders",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("existing")
                .doesNotContain("generated", "different-response-id");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation ->
                Assertions.assertThat(correlation.getSourceLocation()).isEqualTo("request.path.customerId"));
    }

    @Test
    void shouldExtractSuccessfulItemPathValuesWithTrailingSlash() {
        enableResourceReuse();
        ServiceData itemGet = getData("/customers/{customerId}/", "{\"customerId\":\"existing\"}");
        resourcePool.observe(itemGet, request("GET", "/customers/existing/", itemGet.getPayload()),
                response(200, "{}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}/orders",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("existing").doesNotContain("generated");
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
    void shouldReusePatchResponseResources() {
        enableResourceReuse();
        ServiceData patch = ServiceData.builder().relativePath("/customers/{customerId}")
                .payload("{\"name\":\"updated\"}").httpMethod(HttpMethod.PATCH)
                .contentType("application/merge-patch+json").build();
        resourcePool.observe(patch, request("PATCH", "/customers/customer-42", patch.getPayload()),
                response(200, "{\"id\":\"customer-42\",\"name\":\"updated\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(
                getData("/customers/{customerId}", "{\"customerId\":\"generated\"}"),
                "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-42").doesNotContain("generated");
    }

    @Test
    void shouldReusePrimitiveValuesFromSuccessfulRequestBodies() {
        enableResourceReuse();
        ServiceData create = postData("/customers", "{\"externalId\":\"external-42\",\"name\":\"customer\"}");
        resourcePool.observe(create, request("POST", "/customers", create.getPayload()),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("external-42").doesNotContain("generated");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation ->
                Assertions.assertThat(correlation.getSourceLocation()).isEqualTo("request.body.$.externalId"));
    }

    @Test
    void shouldPreferResponseValueOverSuccessfulRequestValue() {
        enableResourceReuse();
        ServiceData create = postData("/customers", "{\"id\":\"client-id\"}");
        resourcePool.observe(create, request("POST", "/customers", create.getPayload()),
                response(201, "{\"id\":\"server-id\"}"));
        ServiceData target = getData("/customers/{id}", "{\"id\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("server-id").doesNotContain("client-id");
        Assertions.assertThat(result.correlations()).singleElement().satisfies(correlation ->
                Assertions.assertThat(correlation.getSourceLocation()).isEqualTo("response.body.$.id"));
    }

    @Test
    void shouldCaptureSuccessfulRequestBodyWhenResponseIsEmpty() {
        enableResourceReuse();
        ServiceData update = ServiceData.builder().relativePath("/customers").payload("{\"externalId\":\"external-42\"}")
                .httpMethod(HttpMethod.PUT).contentType("application/json").build();
        resourcePool.observe(update, request("PUT", "/customers", update.getPayload()), response(204, ""));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("external-42").doesNotContain("generated");
    }

    @Test
    void shouldAlignBulkRequestAndResponseObjectsByIndex() {
        enableResourceReuse();
        ServiceData bulk = postData("/customers/batch",
                "[{\"externalId\":\"external-1\"},{\"externalId\":\"external-2\"}]");
        resourcePool.observe(bulk, request("POST", "/customers/batch", bulk.getPayload()),
                response(201, "[{\"id\":\"customer-1\"},{\"id\":\"customer-2\"}]"));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("external-2").doesNotContain("generated");
    }

    @Test
    void shouldNotGuessBulkPairingWhenRequestAndResponseCountsDiffer() {
        enableResourceReuse();
        ServiceData bulk = postData("/customers/batch", "[{\"externalId\":\"external-1\"}]");
        resourcePool.observe(bulk, request("POST", "/customers/batch", bulk.getPayload()),
                response(201, "[{\"id\":\"customer-1\"},{\"id\":\"customer-2\"}]"));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("generated").doesNotContain("external-1");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldNotPairBulkObjectsAcrossNonObjectRequestElements() {
        enableResourceReuse();
        ServiceData bulk = postData("/customers/batch",
                "[{\"externalId\":\"external-1\"},null,{\"externalId\":\"external-3\"}]");
        resourcePool.observe(bulk, request("POST", "/customers/batch", bulk.getPayload()),
                response(201, "[{\"id\":\"customer-1\"},{\"id\":\"customer-2\"}]"));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("generated")
                .doesNotContain("external-1", "external-3");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldNotPairBulkObjectsAcrossNonObjectResponseElements() {
        enableResourceReuse();
        ServiceData bulk = postData("/customers/batch",
                "[{\"externalId\":\"external-1\"},{\"externalId\":\"external-2\"}]");
        resourcePool.observe(bulk, request("POST", "/customers/batch", bulk.getPayload()),
                response(201, "[{\"id\":\"customer-1\"},null,{\"id\":\"customer-3\"}]"));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("generated")
                .doesNotContain("external-1", "external-2");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldNotCaptureMutatedRequestBodyValues() {
        enableResourceReuse();
        ServiceData fuzzedCreate = ServiceData.builder().relativePath("/customers")
                .payload("{\"externalId\":\"mutated-external\"}").mutationTarget(RequestTarget.body("externalId"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedCreate, request("POST", "/customers", fuzzedCreate.getPayload()),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData target = postData("/orders", "{\"externalId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("generated").doesNotContain("mutated-external");
    }

    @Test
    void shouldReuseValidBodyDataWhenOnlyAHeaderWasFuzzed() {
        enableResourceReuse();
        String original = "{\"externalId\":\"generated\"}";
        ServiceData headerFuzzed = ServiceData.builder().relativePath("/customers").contractPath("/customers")
                .payload(original).originalPayload(original).mutationTarget(RequestTarget.header("X-Trace"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(headerFuzzed, request("POST", "/customers", "{\"externalId\":\"accepted\"}"),
                response(201, "{\"id\":\"customer-42\"}"));

        String baseline = resourcePool.applySuccessfulRequestBaseline(bodyData("/customers", HttpMethod.POST,
                original, original));
        RuntimeResourcePool.ResolvedRequest correlated = resourcePool.enrich(
                postData("/orders", "{\"externalId\":\"generated\"}"), "{\"externalId\":\"generated\"}");

        Assertions.assertThat(baseline).contains("accepted").doesNotContain("generated");
        Assertions.assertThat(correlated.payload()).contains("accepted").doesNotContain("generated");
    }

    @Test
    void shouldReuseSuccessfulRequestAsBaselineWhilePreservingReplacementMutation() {
        enableResourceReuse();
        String original = "{\"country\":\"generated\",\"currency\":\"USD\",\"name\":\"generated\"}";
        ServiceData clean = bodyData("/customers", HttpMethod.POST, original, original);
        resourcePool.observe(clean, request("POST", "/customers",
                        "{\"country\":\"DE\",\"currency\":\"EUR\",\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzed = bodyData("/customers", HttpMethod.POST, original,
                "{\"country\":\"generated\",\"currency\":\"USD\",\"name\":\"invalid\"}");

        String result = resourcePool.applySuccessfulRequestBaseline(fuzzed);

        Assertions.assertThat(result).contains("\"country\":\"DE\"", "\"currency\":\"EUR\"", "\"name\":\"invalid\"")
                .doesNotContain("\"name\":\"accepted\"");
    }

    @Test
    void shouldApplyPrefixMutationToSuccessfulBaselineValue() {
        enableResourceReuse();
        String original = "{\"customerId\":\"generated\",\"name\":\"generated\"}";
        ServiceData clean = bodyData("/orders", HttpMethod.POST, original, original);
        resourcePool.observe(clean, request("POST", "/orders",
                        "{\"customerId\":\"customer-42\",\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"order-42\"}"));
        ServiceData fuzzed = bodyData("/orders", HttpMethod.POST, original,
                "{\"customerId\":\"  generated\",\"name\":\"generated\"}");

        String result = resourcePool.applySuccessfulRequestBaseline(fuzzed);

        Assertions.assertThat(result).contains("\"customerId\":\"  customer-42\"", "\"name\":\"accepted\"");
    }

    @Test
    void shouldReplaceAStaleBaselineIdentifierWithAnActiveRuntimeResource() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-old\"}"));
        String original = "{\"customerId\":\"generated\",\"name\":\"generated\"}";
        ServiceData cleanOrder = bodyData("/orders", HttpMethod.POST, original, original);
        resourcePool.observe(cleanOrder, request("POST", "/orders",
                        "{\"customerId\":\"customer-old\",\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"order-42\"}"));
        ServiceData delete = ServiceData.builder().relativePath("/customers/{customerId}")
                .httpMethod(HttpMethod.DELETE).contentType("application/json").build();
        resourcePool.observe(delete, request("DELETE", "/customers/customer-old", "{}"), response(204, ""));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-new\"}"));
        ServiceData fuzzedOrder = ServiceData.builder().relativePath("/orders").contractPath("/orders")
                .payload("{\"customerId\":\"generated\",\"name\":\"invalid\"}").originalPayload(original)
                .mutationTarget(RequestTarget.body("name")).httpMethod(HttpMethod.POST)
                .contentType("application/json").build();

        String baseline = resourcePool.applySuccessfulRequestBaseline(fuzzedOrder);
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedOrder, baseline);

        Assertions.assertThat(baseline).contains("generated", "invalid").doesNotContain("customer-old");
        Assertions.assertThat(result.payload()).contains("customer-new", "invalid").doesNotContain("customer-old");
    }

    @Test
    void shouldPreserveAddedAndRemovedFieldsWhenApplyingSuccessfulBaseline() {
        enableResourceReuse();
        String original = "{\"kept\":\"generated\",\"removed\":\"generated\"}";
        ServiceData clean = bodyData("/orders", HttpMethod.PUT, original, original);
        resourcePool.observe(clean, request("PUT", "/orders",
                        "{\"kept\":\"accepted\",\"removed\":\"accepted\",\"serverDefault\":\"present\"}"),
                response(200, "{\"id\":\"order-42\"}"));
        ServiceData fuzzed = bodyData("/orders", HttpMethod.PUT, original,
                "{\"kept\":\"generated\",\"added\":\"fuzzed\"}");

        String result = resourcePool.applySuccessfulRequestBaseline(fuzzed);

        Assertions.assertThat(result).contains("\"kept\":\"accepted\"", "\"added\":\"fuzzed\"",
                        "\"serverDefault\":\"present\"")
                .doesNotContain("removed");
    }

    @Test
    void shouldScopeSuccessfulBaselinesByOperationMethodContentTypeAndVariant() {
        enableResourceReuse();
        String original = "{\"variantA\":\"generated\"}";
        ServiceData clean = bodyData("/orders", HttpMethod.POST, original, original);
        resourcePool.observe(clean, request("POST", "/orders", "{\"variantA\":\"accepted\"}"),
                response(201, "{\"id\":\"order-42\"}"));

        ServiceData otherPath = bodyData("/invoices", HttpMethod.POST, original, original);
        ServiceData otherMethod = bodyData("/orders", HttpMethod.PUT, original, original);
        ServiceData otherVariant = bodyData("/orders", HttpMethod.POST,
                "{\"variantB\":\"generated\"}", "{\"variantB\":\"generated\"}");
        ServiceData otherContentType = ServiceData.builder().relativePath("/orders").contractPath("/orders")
                .payload(original).originalPayload(original).httpMethod(HttpMethod.POST)
                .contentType("application/problem+json").build();

        Assertions.assertThat(resourcePool.applySuccessfulRequestBaseline(otherPath)).isEqualTo(original);
        Assertions.assertThat(resourcePool.applySuccessfulRequestBaseline(otherMethod)).isEqualTo(original);
        Assertions.assertThat(resourcePool.applySuccessfulRequestBaseline(otherVariant)).contains("variantB", "generated");
        Assertions.assertThat(resourcePool.applySuccessfulRequestBaseline(otherContentType)).isEqualTo(original);
    }

    @Test
    void shouldShareSuccessfulBaselineAcrossJsonContentTypeParameters() {
        enableResourceReuse();
        String original = "{\"name\":\"generated\"}";
        ServiceData clean = ServiceData.builder().relativePath("/customers").contractPath("/customers")
                .payload(original).originalPayload(original).httpMethod(HttpMethod.POST)
                .contentType("application/json; charset=UTF-8").build();
        resourcePool.observe(clean, request("POST", "/customers", "{\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"customer-42\"}"));

        String result = resourcePool.applySuccessfulRequestBaseline(
                bodyData("/customers", HttpMethod.POST, original, original));

        Assertions.assertThat(result).contains("accepted").doesNotContain("generated");
    }

    @Test
    void shouldNotReplaceCleanBaselineWithSuccessfulFuzzedRequest() {
        enableResourceReuse();
        String original = "{\"country\":\"generated\",\"name\":\"generated\"}";
        ServiceData clean = bodyData("/customers", HttpMethod.POST, original, original);
        resourcePool.observe(clean, request("POST", "/customers",
                        "{\"country\":\"DE\",\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzed = ServiceData.builder().relativePath("/customers").contractPath("/customers")
                .payload("{\"country\":\"XX\",\"name\":\"mutated\"}").originalPayload(original)
                .mutationTarget(RequestTarget.body("name")).httpMethod(HttpMethod.POST)
                .contentType("application/json").build();
        resourcePool.observe(fuzzed, request("POST", "/customers", fuzzed.getPayload()),
                response(201, "{\"id\":\"customer-43\"}"));
        ServiceData later = bodyData("/customers", HttpMethod.POST, original, original);

        String result = resourcePool.applySuccessfulRequestBaseline(later);

        Assertions.assertThat(result).contains("\"country\":\"DE\"", "\"name\":\"accepted\"")
                .doesNotContain("mutated", "\"country\":\"XX\"");
    }

    @Test
    void shouldBypassSuccessfulBaselineWhenReplacementOrJsonProcessingIsDisabled() {
        enableResourceReuse();
        String original = "{\"name\":\"generated\"}";
        ServiceData clean = bodyData("/customers", HttpMethod.POST, original, original);
        resourcePool.observe(clean, request("POST", "/customers", "{\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData noReplacement = ServiceData.builder().relativePath("/customers").contractPath("/customers")
                .payload(original).originalPayload(original).httpMethod(HttpMethod.POST)
                .contentType("application/json").replaceRefData(false).build();
        ServiceData invalidJson = ServiceData.builder().relativePath("/customers").contractPath("/customers")
                .payload("not-json").originalPayload(original).httpMethod(HttpMethod.POST)
                .contentType("application/json").validJson(false).build();

        Assertions.assertThat(resourcePool.applySuccessfulRequestBaseline(noReplacement)).isEqualTo(original);
        Assertions.assertThat(resourcePool.applySuccessfulRequestBaseline(invalidJson)).isEqualTo("not-json");
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
    void shouldReuseNonIdentifierValuesInQueryParameters() {
        enableResourceReuse();
        resourcePool.observe(postData("/entries", "{}"), request("POST", "/entries", "{}"),
                response(201, "{\"locator\":\"issued-locator\"}"));
        ServiceData query = ServiceData.builder().relativePath("/entries")
                .payload("{\"locator\":\"generated\"}").queryParams(Set.of("locator"))
                .httpMethod(HttpMethod.GET).contentType("application/json").build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(query, query.getPayload());

        Assertions.assertThat(result.payload()).contains("issued-locator").doesNotContain("generated");
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
    void shouldUseIdentifierFromRelativeLocationWithTrailingSlash() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"status\":\"created\"}")
                .headers(List.of(new KeyValuePair<>("Location", "/customers/customer-42/")))
                .build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-42").doesNotContain("generated");
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
                .mutationTarget(RequestTarget.body("name")).httpMethod(HttpMethod.POST)
                .contentType("application/json").build();
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
    void shouldPreferCleanResourcesOverMorePathAffineFuzzedResources() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"clean-customer\"}"));
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/customers/{customerId}/orders")
                .payload("{\"name\":\"mutated\"}").mutationTarget(RequestTarget.body("name"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/customers/fuzzed-customer/orders", fuzzedPost.getPayload()),
                response(201, "{\"status\":\"created\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}/orders",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("clean-customer").doesNotContain("fuzzed-customer");
    }

    @Test
    void shouldReuseFuzzedResourceWhenNoCleanCandidateExists() {
        enableResourceReuse();
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/customers/{customerId}/orders")
                .payload("{\"name\":\"mutated\"}").mutationTarget(RequestTarget.body("name"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/customers/fuzzed-customer/orders", fuzzedPost.getPayload()),
                response(201, "{\"status\":\"created\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}/orders",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("fuzzed-customer").doesNotContain("generated");
    }

    @Test
    void shouldTryUnpenalizedFuzzedCandidateAfterCleanCandidateFails() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"clean-customer\"}"));
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/customers/{customerId}/orders")
                .payload("{\"name\":\"mutated\"}").mutationTarget(RequestTarget.body("name"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/customers/fuzzed-customer/orders", fuzzedPost.getPayload()),
                response(201, "{\"status\":\"created\"}"));
        ServiceData target = getData("/customers/{customerId}/orders", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest first = resourcePool.enrich(target, target.getPayload());

        resourcePool.observe(target, request("GET", "/customers/clean-customer/orders", first.payload()),
                response(404, "{}"), first);
        RuntimeResourcePool.ResolvedRequest second = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(first.payload()).contains("clean-customer");
        Assertions.assertThat(second.payload()).contains("fuzzed-customer").doesNotContain("clean-customer");
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409, 410, 422})
    void shouldTryAnotherCandidateAfterCorrelationFailure(int responseCode) {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-2\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest first = resourcePool.enrich(target, target.getPayload());

        resourcePool.observe(target, request("GET", "/customers/customer-2", first.payload()),
                response(responseCode, "{}"), first);
        RuntimeResourcePool.ResolvedRequest second = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(first.payload()).contains("customer-2");
        Assertions.assertThat(second.payload()).contains("customer-1").doesNotContain("customer-2");
    }

    @Test
    void shouldDiscardPendingFeedbackWhenARequestIsNotCompleted() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-2\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest abandoned = resourcePool.enrich(target, target.getPayload());

        resourcePool.discard(abandoned);
        resourcePool.observe(target, request("GET", "/customers/customer-2", abandoned.payload()),
                response(404, "{}"), abandoned);
        RuntimeResourcePool.ResolvedRequest next = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(abandoned.payload()).contains("customer-2");
        Assertions.assertThat(next.payload()).contains("customer-2").doesNotContain("customer-1");
    }

    @Test
    void shouldKeepUsingPenalizedCandidateWhenItIsTheOnlyMatch() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest first = resourcePool.enrich(target, target.getPayload());

        resourcePool.observe(target, request("GET", "/customers/customer-1", first.payload()),
                response(404, "{}"), first);
        RuntimeResourcePool.ResolvedRequest second = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(second.payload()).contains("customer-1").doesNotContain("generated");
    }

    @Test
    void shouldKeepCorrelationFeedbackSpecificToTheDestinationPath() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-2\"}"));
        ServiceData itemTarget = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest itemRequest = resourcePool.enrich(itemTarget, itemTarget.getPayload());
        resourcePool.observe(itemTarget, request("GET", "/customers/customer-2", itemRequest.payload()),
                response(404, "{}"), itemRequest);
        ServiceData ordersTarget = getData("/customers/{customerId}/orders", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest ordersRequest = resourcePool.enrich(ordersTarget, ordersTarget.getPayload());

        Assertions.assertThat(ordersRequest.payload()).contains("customer-2").doesNotContain("customer-1");
    }

    @Test
    void shouldNotPenalizeCorrelationsForIntentionalFuzzingFailures() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-2\"}"));
        ServiceData fuzzedTarget = ServiceData.builder().relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\",\"filter\":\"invalid\"}")
                .originalPayload("{\"customerId\":\"generated\",\"filter\":\"valid\"}")
                .mutationTarget(RequestTarget.query("filter")).queryParams(Set.of("filter"))
                .httpMethod(HttpMethod.GET).contentType("application/json").build();
        RuntimeResourcePool.ResolvedRequest fuzzedRequest = resourcePool.enrich(fuzzedTarget, fuzzedTarget.getPayload());
        resourcePool.observe(fuzzedTarget, request("GET", "/customers/customer-2", fuzzedRequest.payload()),
                response(422, "{}"), fuzzedRequest);
        ServiceData cleanTarget = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest cleanRequest = resourcePool.enrich(cleanTarget, cleanTarget.getPayload());

        Assertions.assertThat(cleanRequest.payload()).contains("customer-2").doesNotContain("customer-1");
    }

    @Test
    void shouldRecoverCandidateConfidenceAfterSuccessfulReuse() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest failedRequest = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/customers/customer-1", failedRequest.payload()),
                response(404, "{}"), failedRequest);
        RuntimeResourcePool.ResolvedRequest successfulRequest = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/customers/customer-1", successfulRequest.payload()),
                response(200, "{\"id\":\"customer-1\"}"), successfulRequest);
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-2\"}"));

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("customer-1").doesNotContain("customer-2");
    }

    @Test
    void shouldPreferCandidateWithTheSamePrimitiveType() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":42}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"newer-string-id\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":999}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("\"customerId\":42").doesNotContain("newer-string-id");
    }

    @Test
    void shouldPreferCandidateWithTheSameUuidShape() {
        enableResourceReuse();
        String uuid = "123e4567-e89b-12d3-a456-426614174000";
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"" + uuid + "\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"newer-generic-id\"}"));
        ServiceData target = getData("/customers/{customerId}",
                "{\"customerId\":\"00000000-0000-0000-0000-000000000000\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains(uuid).doesNotContain("newer-generic-id");
    }

    @Test
    void shouldPreferCandidateWithTheSameNumericStringShape() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"12345\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"newer-generic-id\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"99999\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("12345").doesNotContain("newer-generic-id");
    }

    @Test
    void shouldKeepGenericStringTargetsPermissive() {
        enableResourceReuse();
        String newerUuid = "123e4567-e89b-12d3-a456-426614174000";
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"older-generic-id\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"" + newerUuid + "\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains(newerUuid).doesNotContain("older-generic-id");
    }

    @Test
    void shouldKeepUsingTypeMismatchWhenItIsTheOnlyCandidate() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"string-id\"}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":999}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("string-id").doesNotContain("999");
    }

    @Test
    void shouldPreferCompatibleFuzzedCandidateOverIncompatibleCleanCandidate() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"clean-string-id\"}"));
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/customers")
                .payload("{\"name\":\"mutated\"}").mutationTarget(RequestTarget.body("name"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/customers", fuzzedPost.getPayload()),
                response(201, "{\"id\":42}"));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":999}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("\"customerId\":42").doesNotContain("clean-string-id");
    }

    @Test
    void shouldPreferResourceFamilyMatchOverGenericPathAffinity() {
        enableResourceReuse();
        resourcePool.observe(postData("/orders", "{}"), request("POST", "/orders", "{}"),
                response(201, "{\"id\":\"order-1\"}"));
        resourcePool.observe(postData("/api/customers", "{}"), request("POST", "/api/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        ServiceData target = getData("/api/orders/{id}", "{\"id\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("order-1").doesNotContain("customer-1");
    }

    @Test
    void shouldKeepUsingGenericIdentifierWhenItIsTheOnlyCandidate() {
        enableResourceReuse();
        resourcePool.observe(postData("/api/customers", "{}"), request("POST", "/api/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        ServiceData target = getData("/api/orders/{id}", "{\"id\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("customer-1").doesNotContain("generated");
    }

    @Test
    void shouldPreferSemanticallyQualifiedFuzzedCandidateOverGenericCleanCandidate() {
        enableResourceReuse();
        resourcePool.observe(postData("/api/customers", "{}"), request("POST", "/api/customers", "{}"),
                response(201, "{\"id\":\"clean-customer\"}"));
        ServiceData fuzzedPost = ServiceData.builder().relativePath("/orders")
                .payload("{\"name\":\"mutated\"}").mutationTarget(RequestTarget.body("name"))
                .httpMethod(HttpMethod.POST).contentType("application/json").build();
        resourcePool.observe(fuzzedPost, request("POST", "/orders", fuzzedPost.getPayload()),
                response(201, "{\"id\":\"fuzzed-order\"}"));
        ServiceData target = getData("/api/orders/{id}", "{\"id\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("fuzzed-order").doesNotContain("clean-customer");
    }

    @Test
    void shouldTryUnpenalizedGenericCandidateAfterQualifiedCandidateFails() {
        enableResourceReuse();
        resourcePool.observe(postData("/orders", "{}"), request("POST", "/orders", "{}"),
                response(201, "{\"id\":\"order-1\"}"));
        resourcePool.observe(postData("/api/customers", "{}"), request("POST", "/api/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        ServiceData target = getData("/api/orders/{id}", "{\"id\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest first = resourcePool.enrich(target, target.getPayload());

        resourcePool.observe(target, request("GET", "/api/orders/order-1", first.payload()),
                response(404, "{}"), first);
        RuntimeResourcePool.ResolvedRequest second = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(first.payload()).contains("order-1");
        Assertions.assertThat(second.payload()).contains("customer-1").doesNotContain("order-1");
    }

    @Test
    void shouldPreferTheLessPenalizedCandidateAfterAllCandidatesFail() {
        enableResourceReuse();
        resourcePool.observe(postData("/orders", "{}"), request("POST", "/orders", "{}"),
                response(201, "{\"id\":\"order-1\"}"));
        resourcePool.observe(postData("/api/customers", "{}"), request("POST", "/api/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        ServiceData target = getData("/api/orders/{id}", "{\"id\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest first = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/api/orders/order-1", first.payload()),
                response(404, "{}"), first);
        RuntimeResourcePool.ResolvedRequest second = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/api/orders/customer-1", second.payload()),
                response(404, "{}"), second);
        RuntimeResourcePool.ResolvedRequest third = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/api/orders/order-1", third.payload()),
                response(404, "{}"), third);

        RuntimeResourcePool.ResolvedRequest fourth = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(first.payload()).contains("order-1");
        Assertions.assertThat(second.payload()).contains("customer-1");
        Assertions.assertThat(third.payload()).contains("order-1");
        Assertions.assertThat(fourth.payload()).contains("customer-1").doesNotContain("order-1");
    }

    @Test
    void shouldKeepRelatedFieldsBoundToTheSameResourceInstance() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"customerId\":\"customer-A\",\"customerExternalId\":\"external-A\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"customerId\":\"customer-B\"}"));
        ServiceData target = postData("/orders",
                "{\"customerExternalId\":\"generated-external\",\"customerId\":\"generated-customer\"}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("external-A", "customer-A").doesNotContain("customer-B");
    }

    @Test
    void shouldLetCompatibilityOverrideRequestScopedBinding() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"customerId\":\"customer-A\",\"customerExternalId\":\"external-A\"}"));
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"customerId\":42}"));
        ServiceData target = postData("/orders",
                "{\"customerExternalId\":\"generated-external\",\"customerId\":999}");

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(target, target.getPayload());

        Assertions.assertThat(result.payload()).contains("external-A", "\"customerId\":42")
                .doesNotContain("\"customerId\":\"customer-A\"");
    }

    @Test
    void shouldKeepCollectionEtagSeparateFromItemResources() {
        enableResourceReuse();
        CatsResponse collection = CatsResponse.builder().responseCode(200)
                .body("[{\"id\":\"customer-1\"},{\"id\":\"customer-2\"}]")
                .headers(List.of(new KeyValuePair<>("ETag", "\"collection-version\""))).build();
        ServiceData collectionGet = getData("/customers", "{}");
        resourcePool.observe(collectionGet, request("GET", "/customers", "{}"), collection);
        ServiceData itemTarget = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest itemRequest = resourcePool.enrich(itemTarget, itemTarget.getPayload());

        RuntimeResourcePool.ResolvedHeaders itemHeaders = resourcePool.enrichHeaders(itemTarget,
                List.of(new KeyValuePair<>("If-Match", "generated")), itemRequest);
        RuntimeResourcePool.ResolvedHeaders collectionHeaders = resourcePool.enrichHeaders(collectionGet,
                List.of(new KeyValuePair<>("If-None-Match", "generated")));

        Assertions.assertThat(itemHeaders.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("generated"));
        Assertions.assertThat(collectionHeaders.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("\"collection-version\""));
    }

    @Test
    void shouldReusePathAndEtagFromSuccessfulHeadResponse() {
        enableResourceReuse();
        ServiceData head = ServiceData.builder().relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}").httpMethod(HttpMethod.HEAD)
                .contentType("application/json").build();
        CatsResponse response = CatsResponse.builder().responseCode(200).body("")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-7\""))).build();
        resourcePool.observe(head, request("HEAD", "/customers/customer-42", ""), response);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedRequest correlated = resourcePool.enrich(target, target.getPayload());
        RuntimeResourcePool.ResolvedHeaders headers = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "generated")), correlated);

        Assertions.assertThat(correlated.payload()).contains("customer-42").doesNotContain("generated");
        Assertions.assertThat(headers.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("\"version-7\""));
    }

    @Test
    void shouldReuseEtagForConditionalRequestHeaders() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-7\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "generated"),
                        new KeyValuePair<>("If-None-Match", "generated")));

        Assertions.assertThat(result.headers()).extracting(KeyValuePair::getValue)
                .containsExactly("\"version-7\"", "\"version-7\"");
        Assertions.assertThat(result.correlations()).extracting(ResourceCorrelation::getTargetField)
                .containsExactlyInAnyOrder("If-Match", "If-None-Match");
    }

    @Test
    void shouldReuseConditionalHeaderFromTheCorrelatedPathResource() {
        enableResourceReuse();
        CatsResponse firstCreated = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-1\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-1\""))).build();
        CatsResponse secondCreated = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-2\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-2\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), firstCreated);
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), secondCreated);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest failed = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/customers/customer-2", failed.payload()),
                response(404, "{}"), failed);
        RuntimeResourcePool.ResolvedRequest correlated = resourcePool.enrich(target, target.getPayload());

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "generated")), correlated);

        Assertions.assertThat(correlated.payload()).contains("customer-1").doesNotContain("customer-2");
        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("\"version-1\""));
    }

    @Test
    void shouldNotMixAConditionalHeaderFromAnotherPathResource() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-1\"}"));
        CatsResponse secondCreated = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-2\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-2\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), secondCreated);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        RuntimeResourcePool.ResolvedRequest failed = resourcePool.enrich(target, target.getPayload());
        resourcePool.observe(target, request("GET", "/customers/customer-2", failed.payload()),
                response(404, "{}"), failed);
        RuntimeResourcePool.ResolvedRequest correlated = resourcePool.enrich(target, target.getPayload());

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "generated")), correlated);

        Assertions.assertThat(correlated.payload()).contains("customer-1").doesNotContain("customer-2");
        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("generated"));
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldNotGuessConditionalHeaderWithoutAPathResourceBinding() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-7\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"explicit-customer\"}");
        RuntimeResourcePool.ResolvedRequest unbound = new RuntimeResourcePool.ResolvedRequest(
                target.getPayload(), target.getPathParamsPayload(), List.of());

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "generated")), unbound);

        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("generated"));
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldReuseExactVersionResponseHeader() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("X-Resource-Version", "17"))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("X-Resource-Version", "generated")));

        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("17"));
    }

    @Test
    void shouldPreserveDeliberatelyFuzzedConditionalHeader() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-7\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ServiceData target = ServiceData.builder().relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}").mutationTarget(RequestTarget.header("If-Match"))
                .httpMethod(HttpMethod.GET).contentType("application/json").build();

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "fuzzed")));

        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("fuzzed"));
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldMergePrefixHeaderMutationWithCorrelatedEtag() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-7\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ServiceData target = ServiceData.builder().relativePath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}").mutationTarget(RequestTarget.header("If-Match"))
                .httpMethod(HttpMethod.GET).contentType("application/json").build();

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "  generated")));

        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("  \"version-7\""));
        Assertions.assertThat(result.correlations()).hasSize(1);
    }

    @Test
    void shouldPreserveExplicitlySuppliedConditionalHeader() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("ETag", "\"version-7\""))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ReflectionTestUtils.setField(filesArguments, "headers",
                Map.of("/customers/{customerId}", Map.of("If-Match", "explicit")));
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("If-Match", "explicit")));

        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("explicit"));
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldNotReuseUnselectedSensitiveResponseHeaders() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"id\":\"customer-42\"}")
                .headers(List.of(new KeyValuePair<>("Set-Cookie", "session=secret"))).build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);
        ServiceData target = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");

        RuntimeResourcePool.ResolvedHeaders result = resourcePool.enrichHeaders(target,
                List.of(new KeyValuePair<>("Set-Cookie", "generated")));

        Assertions.assertThat(result.headers()).singleElement().satisfies(header ->
                Assertions.assertThat(header.getValue()).isEqualTo("generated"));
    }

    @Test
    void shouldUseContentLocationAsResourceIdentifierFallback() {
        enableResourceReuse();
        CatsResponse created = CatsResponse.builder().responseCode(201).body("{\"status\":\"created\"}")
                .headers(List.of(new KeyValuePair<>("Content-Location", "http://localhost/customers/customer-42")))
                .build();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"), created);

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(
                getData("/customers/{customerId}", "{\"customerId\":\"generated\"}"),
                "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-42").doesNotContain("generated");
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
        Assertions.assertThat(result.correlations()).extracting(ResourceCorrelation::getTargetField)
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
        Assertions.assertThat(result.correlations()).extracting(ResourceCorrelation::getTargetField)
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
                .mutationTarget(RequestTarget.path("customerId"))
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
                .mutationTarget(RequestTarget.path("customerId"))
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.payload()).contains("invalid-id").doesNotContain("customer-42");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldNotOverrideTypedPathMutation() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData fuzzedData = ServiceData.builder()
                .relativePath("/customers/{customerId}")
                .contractPath("/customers/{customerId}")
                .payload("{\"customerId\":\"invalid-id\"}")
                .pathParamsPayload("{\"customerId\":\"invalid-id\"}")
                .queryParams(Set.of())
                .mutationTarget(RequestTarget.path("customerId"))
                .httpMethod(HttpMethod.GET)
                .contentType("application/json")
                .build();

        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(fuzzedData, fuzzedData.getPayload());

        Assertions.assertThat(result.pathParamsPayload()).contains("invalid-id").doesNotContain("customer-42");
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
    void shouldCaptureSafeLocationDataFromTruncatedSuccessfulResponse() {
        enableResourceReuse();
        CatsResponse truncated = CatsResponse.builder().responseCode(201).body("partial")
                .bodyTruncated(true)
                .headers(List.of(new KeyValuePair<>("Location", "http://localhost/customers/customer-42")))
                .build();

        resourcePool.observe(postData("/customers", "{\"name\":\"accepted\"}"),
                request("POST", "/customers", "{\"name\":\"accepted\"}"), truncated);
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("customer-42").doesNotContain("generated");
    }

    @Test
    void shouldInvalidateResourceAfterTruncatedSuccessfulDelete() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData delete = ServiceData.builder().relativePath("/customers/{customerId}")
                .httpMethod(HttpMethod.DELETE).contentType("application/json").build();
        CatsResponse truncated = CatsResponse.builder().responseCode(204).body("partial").bodyTruncated(true).build();

        resourcePool.observe(delete, request("DELETE", "/customers/customer-42", "{}"), truncated);
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("generated");
        Assertions.assertThat(result.correlations()).isEmpty();
    }

    @Test
    void shouldInvalidateSuccessfulBaselinesContainingADeletedIdentifier() {
        enableResourceReuse();
        String original = "{\"customerId\":\"generated\",\"name\":\"generated\"}";
        ServiceData order = bodyData("/orders", HttpMethod.POST, original, original);
        resourcePool.observe(order, request("POST", "/orders",
                        "{\"customerId\":\"customer-42\",\"name\":\"accepted\"}"),
                response(201, "{\"id\":\"order-42\"}"));
        ServiceData delete = ServiceData.builder().relativePath("/customers/{customerId}")
                .httpMethod(HttpMethod.DELETE).contentType("application/json").build();

        resourcePool.observe(delete, request("DELETE", "/customers/customer-42", "{}"), response(204, ""));
        String result = resourcePool.applySuccessfulRequestBaseline(
                bodyData("/orders", HttpMethod.POST, original, original));

        Assertions.assertThat(result).isEqualTo(original);
    }

    @Test
    void shouldInvalidateResourceDeletedByIdentifierQueryParameter() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        ServiceData delete = ServiceData.builder().relativePath("/customers")
                .payload("{\"customerId\":\"customer-42\"}").queryParams(Set.of("customerId"))
                .httpMethod(HttpMethod.DELETE).contentType("application/json").build();

        resourcePool.observe(delete,
                request("DELETE", "/customers?customerId=customer-42", delete.getPayload()), response(204, ""));
        RuntimeResourcePool.ResolvedRequest result = resourcePool.enrich(getData("/customers/{customerId}",
                "{\"customerId\":\"generated\"}"), "{\"customerId\":\"generated\"}");

        Assertions.assertThat(result.payload()).contains("generated");
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

    @Test
    void shouldReportPathParameterWithoutCapturedValueAndFailingProducer() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{\"name\":\"x\"}"), request("POST", "/customers", "{\"name\":\"x\"}"),
                response(400, "{}"));
        resourcePool.observe(getData("/customers/{customerId}", "{\"customerId\":\"generated\"}"),
                request("GET", "/customers/generated", "{\"customerId\":\"generated\"}"), response(404, "{}"));
        resourcePool.observe(getData("/customers/{customerId}", "{\"customerId\":\"generated\"}"),
                request("GET", "/customers/generated", "{\"customerId\":\"generated\"}"), response(404, "{}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).singleElement().satisfies(operation -> {
            Assertions.assertThat(operation.outcome().path()).isEqualTo("/customers/{customerId}");
            Assertions.assertThat(operation.outcome().method()).isEqualTo(HttpMethod.GET);
            Assertions.assertThat(operation.outcome().requests()).isEqualTo(2);
            Assertions.assertThat(operation.outcome().successes()).isZero();
            Assertions.assertThat(operation.outcome().statusCodes()).containsExactly(Map.entry(404, 2));
            Assertions.assertThat(operation.parameters()).singleElement().satisfies(parameter -> {
                Assertions.assertThat(parameter.target()).isEqualTo(new RequestTarget(RequestTarget.Location.PATH, "customerId"));
                Assertions.assertThat(parameter.reason()).isEqualTo(RuntimeResourcePool.UnresolvedReason.NO_VALUE_CAPTURED);
                Assertions.assertThat(parameter.producer()).isNotNull();
                Assertions.assertThat(parameter.producer().path()).isEqualTo("/customers");
                Assertions.assertThat(parameter.producer().method()).isEqualTo(HttpMethod.POST);
                Assertions.assertThat(parameter.producer().successes()).isZero();
                Assertions.assertThat(parameter.producer().statusCodes()).containsExactly(Map.entry(400, 1));
            });
        });
    }

    @Test
    void shouldReportCapturedValuesThatAlwaysReturnNotFound() {
        enableResourceReuse();
        resourcePool.observe(postData("/customers", "{}"), request("POST", "/customers", "{}"),
                response(201, "{\"id\":\"customer-42\"}"));
        resourcePool.observe(getData("/customers/{customerId}", "{\"customerId\":\"generated\"}"),
                request("GET", "/customers/customer-42", "{\"customerId\":\"customer-42\"}"), response(404, "{}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).singleElement().satisfies(operation ->
                Assertions.assertThat(operation.parameters()).singleElement().satisfies(parameter -> {
                    Assertions.assertThat(parameter.reason()).isEqualTo(RuntimeResourcePool.UnresolvedReason.CAPTURED_VALUES_NOT_FOUND);
                    Assertions.assertThat(parameter.producer().successes()).isEqualTo(1);
                }));
    }

    @Test
    void shouldReportUnresolvedBodyIdentifier() {
        enableResourceReuse();
        String payload = "{\"customerId\":\"generated\",\"quantity\":1}";
        resourcePool.observe(postData("/orders", payload), request("POST", "/orders", payload), response(422, "{}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).singleElement().satisfies(operation -> {
            Assertions.assertThat(operation.outcome().method()).isEqualTo(HttpMethod.POST);
            Assertions.assertThat(operation.parameters()).singleElement().satisfies(parameter -> {
                Assertions.assertThat(parameter.target()).isEqualTo(RequestTarget.body("customerId"));
                Assertions.assertThat(parameter.reason()).isEqualTo(RuntimeResourcePool.UnresolvedReason.NO_VALUE_CAPTURED);
                Assertions.assertThat(parameter.producer()).isNull();
            });
        });
    }

    @Test
    void shouldNotReportOperationsThatSucceededAtLeastOnce() {
        enableResourceReuse();
        ServiceData get = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(404, "{}"));
        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(200, "{\"id\":\"generated\"}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).isEmpty();
    }

    @Test
    void shouldIgnoreFuzzedPathOverriddenAndSkippedHeaderRequestsInDiagnostics() {
        enableResourceReuse();
        ServiceData fuzzed = ServiceData.builder().relativePath("/customers/{customerId}").contractPath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}").queryParams(Set.of()).httpMethod(HttpMethod.GET)
                .contentType("application/json").mutationTarget(new RequestTarget(RequestTarget.Location.PATH, "customerId")).build();
        ServiceData overriddenPath = ServiceData.builder().relativePath("/customers/123").contractPath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}").queryParams(Set.of()).httpMethod(HttpMethod.GET)
                .contentType("application/json").build();
        ServiceData skippedHeaders = ServiceData.builder().relativePath("/customers/{customerId}").contractPath("/customers/{customerId}")
                .payload("{\"customerId\":\"generated\"}").queryParams(Set.of()).httpMethod(HttpMethod.GET)
                .skippedHeaders(Set.of("Authorization")).contentType("application/json").build();

        resourcePool.observe(fuzzed, request("GET", "/customers/generated", "{}"), response(404, "{}"));
        resourcePool.observe(overriddenPath, request("GET", "/customers/123", "{}"), response(404, "{}"));
        resourcePool.observe(skippedHeaders, request("GET", "/customers/generated", "{}"), response(401, "{}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).isEmpty();
    }

    @Test
    void shouldKnowWhenAnOperationSucceededWithAnUnmodifiedRequest() {
        enableResourceReuse();
        ServiceData get = getData("/customers", "{}");
        ServiceData fuzzedGet = ServiceData.builder().relativePath("/customers").contractPath("/customers").payload("{}")
                .queryParams(Set.of()).httpMethod(HttpMethod.GET).contentType("application/json")
                .mutationTarget(RequestTarget.body("name")).build();

        resourcePool.observe(get, request("GET", "/customers", "{}"), response(500, "{}"));
        resourcePool.observe(fuzzedGet, request("GET", "/customers", "{}"), response(200, "[]"));
        Assertions.assertThat(resourcePool.hasSucceededWithUnmodifiedRequest("/customers", HttpMethod.GET)).isFalse();

        resourcePool.observe(get, request("GET", "/customers", "{}"), response(200, "[]"));
        Assertions.assertThat(resourcePool.hasSucceededWithUnmodifiedRequest("/customers", HttpMethod.GET)).isTrue();
        Assertions.assertThat(resourcePool.hasSucceededWithUnmodifiedRequest("/customers", HttpMethod.POST)).isFalse();
        Assertions.assertThat(resourcePool.hasSucceededWithUnmodifiedRequest("/other", HttpMethod.GET)).isFalse();
    }

    @Test
    void shouldNotKnowSuccessesWhenReuseIsDisabled() {
        ReflectionTestUtils.setField(processingArguments, "reuseSuccessfulResources", false);
        resourcePool.observe(getData("/customers", "{}"), request("GET", "/customers", "{}"), response(200, "[]"));

        Assertions.assertThat(resourcePool.hasSucceededWithUnmodifiedRequest("/customers", HttpMethod.GET)).isFalse();
    }

    @Test
    void shouldNotReportOperationsFailingOnlyWithAuthErrors() {
        enableResourceReuse();
        ServiceData get = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(401, "{}"));
        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(403, "{}"));
        Assertions.assertThat(resourcePool.unresolvedOperations()).isEmpty();

        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(404, "{}"));
        Assertions.assertThat(resourcePool.unresolvedOperations()).hasSize(1);
    }

    @Test
    void shouldNotReportParametersSuppliedThroughUrlParams() {
        enableResourceReuse();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("customerId:supplied"));
        filesArguments.loadURLParams();

        resourcePool.observe(getData("/customers/{customerId}", "{\"customerId\":\"generated\"}"),
                request("GET", "/customers/supplied", "{}"), response(404, "{}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).isEmpty();
    }

    @Test
    void shouldNotRecordDiagnosticsWhenReuseIsDisabledAndClearThemOnReset() {
        ReflectionTestUtils.setField(processingArguments, "reuseSuccessfulResources", false);
        ServiceData get = getData("/customers/{customerId}", "{\"customerId\":\"generated\"}");
        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(404, "{}"));
        Assertions.assertThat(resourcePool.unresolvedOperations()).isEmpty();

        enableResourceReuse();
        resourcePool.observe(get, request("GET", "/customers/generated", "{}"), response(404, "{}"));
        Assertions.assertThat(resourcePool.unresolvedOperations()).hasSize(1);

        resourcePool.clear();
        Assertions.assertThat(resourcePool.unresolvedOperations()).isEmpty();
    }

    @Test
    void shouldTreatNumericallyEqualPathValuesAsNotReplaced() {
        enableResourceReuse();
        resourcePool.observe(getData("/customers/{customerId}", "{\"customerId\":42.0}"),
                request("GET", "/customers/42", "{}"), response(404, "{}"));

        Assertions.assertThat(resourcePool.unresolvedOperations()).singleElement().satisfies(operation ->
                Assertions.assertThat(operation.parameters()).singleElement().satisfies(parameter ->
                        Assertions.assertThat(parameter.reason()).isEqualTo(RuntimeResourcePool.UnresolvedReason.NO_VALUE_CAPTURED)));
    }

    @Test
    void shouldBoundSuccessfulExchangesAndKeepOnlyRelevantHeaders() {
        enableResourceReuse();
        ServiceData get = getData("/items/{id}", "{\"id\":\"generated\"}");
        for (int i = 0; i < 7; i++) {
            resourcePool.observe(get, request("GET", "/items/" + i, "{}"), CatsResponse.builder()
                    .responseCode(200).body("{\"id\":\"" + i + "\"}")
                    .headers(List.of(new KeyValuePair<>("ETag", "\"version-" + i + "\""),
                            new KeyValuePair<>("Location", "/items/" + i),
                            new KeyValuePair<>("Authorization", "secret"))).build());
        }
        Assertions.assertThat(resourcePool.successfulExchanges("/items/{id}", HttpMethod.GET))
                .extracting(RuntimeResourcePool.SuccessfulExchange::url)
                .containsExactly("http://localhost/items/3", "http://localhost/items/4",
                        "http://localhost/items/5", "http://localhost/items/6");
        Assertions.assertThat(resourcePool.successfulExchanges("/items/{id}", HttpMethod.GET).getLast().etag())
                .isEqualTo("\"version-6\"");
        Assertions.assertThat(resourcePool.successfulExchanges("/items/{id}", HttpMethod.GET).getLast().toString())
                .doesNotContain("secret");
        resourcePool.clear();
        Assertions.assertThat(resourcePool.successfulExchanges("/items/{id}", HttpMethod.GET)).isEmpty();
    }

    @Test
    void shouldNotStoreFuzzedOrTruncatedExchanges() {
        enableResourceReuse();
        ServiceData fuzzedGet = ServiceData.builder().relativePath("/items/{id}").contractPath("/items/{id}")
                .payload("{\"id\":\"generated\"}").queryParams(Set.of()).httpMethod(HttpMethod.GET)
                .contentType("application/json").mutationTarget(RequestTarget.path("id")).build();
        resourcePool.observe(fuzzedGet, request("GET", "/items/fuzzed", "{}"), response(200, "{}"));
        resourcePool.observe(getData("/items/{id}", "{\"id\":\"generated\"}"),
                request("GET", "/items/1", "{}"), CatsResponse.builder().responseCode(200)
                        .body("{}").bodyTruncated(true).build());
        Assertions.assertThat(resourcePool.successfulExchanges("/items/{id}", HttpMethod.GET)).isEmpty();
    }

    private void enableResourceReuse() {
        ReflectionTestUtils.setField(processingArguments, "reuseSuccessfulResources", true);
    }

    private ServiceData bodyData(String path, HttpMethod method, String originalPayload, String payload) {
        return ServiceData.builder().relativePath(path).contractPath(path).payload(payload).originalPayload(originalPayload)
                .queryParams(Set.of()).httpMethod(method).contentType("application/json").build();
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
