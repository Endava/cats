package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class CombinatorialBoundaryAndHeaderHavocFuzzerTest {
    private ServiceCaller caller;
    private TestCaseListener listener;
    private CombinatorialBoundaryAndHeaderHavocFuzzer fuzzer;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        caller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(caller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        fuzzer = new CombinatorialBoundaryAndHeaderHavocFuzzer(
                new SimpleExecutor(listener, caller), new RobustnessResponseReporter(listener));
    }

    @Test
    void shouldHaveDescriptionAndSkipBodylessMethods() {
        Assertions.assertThat(fuzzer.description()).contains("locale", "Content-Type", "Unicode", "Base64");
        Assertions.assertThat(fuzzer).hasToString("CombinatorialBoundaryAndHeaderHavocFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSendFourCrossLayerProbesOncePerOperationAndPairHeaderWithBodyTargets() {
        FuzzingData data = sampleData();

        fuzzer.fuzz(data);
        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(4)).call(captor.capture());
        Assertions.assertThat(captor.getAllValues()).allSatisfy(serviceData -> {
            Assertions.assertThat(serviceData.getPayload()).isNotEqualTo(data.getPayload());
            Assertions.assertThat(serviceData.getMutationTargets())
                    .anyMatch(t -> t.location() == RequestTarget.Location.BODY)
                    .anyMatch(t -> t.location() == RequestTarget.Location.HEADER);
            Assertions.assertThat(serviceData.getHeaders())
                    .anyMatch(h -> "X-Keep".equals(h.getName()) && "1".equals(h.getValue()));
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 422, 500, 503})
    void shouldReportResponsesThroughRobustnessReporter(int status) {
        FuzzingData data = sampleData();
        CatsResponse response = CatsResponse.builder().responseCode(status).body("{}").build();
        Mockito.when(caller.call(Mockito.any())).thenReturn(response);

        fuzzer.fuzz(data);

        if (status < 500) {
            Mockito.verify(listener, Mockito.times(4)).reportResultInfo(
                    Mockito.any(), Mockito.same(data), Mockito.anyString(), Mockito.eq(status));
        } else {
            Mockito.verify(listener, Mockito.times(4)).reportResult(
                    Mockito.any(), Mockito.same(data), Mockito.same(response),
                    Mockito.eq(new ResponseCodeFamilyDynamic(List.of("2XX", "3XX", "4XX"))),
                    Mockito.eq(false), Mockito.eq(false));
        }
    }

    @Test
    void shouldNotFailWhenNestedObjectParentAndChildAreBothPresent() {
        Map<String, Schema> schemas = Map.of(
                "externalPspCredentials", new Schema<>().type("object"),
                "externalPspCredentials#endpointPrefix", new Schema<>().type("string"));
        FuzzingData nestedData = FuzzingData.builder()
                .path("/captures")
                .method(HttpMethod.POST)
                .payload("{\"externalPspCredentials\":{\"endpointPrefix\":\"vkuhvk4v\"}}")
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of(CatsHeader.from("X-Keep", "1", false, null)))
                .allFieldsByHttpMethod(schemas.keySet())
                .requestPropertyTypes(schemas)
                .build();

        List<String> present = CombinatorialBoundaryAndHeaderHavocFuzzer.resolvePresentFields(nestedData);
        for (int seed = 0; seed < 50; seed++) {
            CatsRandom.initRandom(seed);
            Assertions.assertThatNoException().isThrownBy(() ->
                    CombinatorialBoundaryAndHeaderHavocFuzzer.buildProbes(nestedData, present));
        }
    }

    @Test
    void shouldSkipNonJsonBodylessOrFieldlessOperations() {
        FuzzingData base = sampleData();
        fuzzer.fuzz(null);
        fuzzer.fuzz(base.toBuilder().method(HttpMethod.GET).build());
        fuzzer.fuzz(base.toBuilder().requestContentTypes(List.of("text/plain")).build());
        fuzzer.fuzz(base.toBuilder().payload("").build());
        fuzzer.fuzz(base.toBuilder().allFieldsByHttpMethod(Set.of("absentField")).build());

        Mockito.verifyNoInteractions(caller);
    }

    private static FuzzingData sampleData() {
        Map<String, Schema> schemas = Map.of(
                "name", new Schema<>().type("string").maxLength(64),
                "amount", new Schema<>().type("integer"),
                "token", new Schema<>().type("string").format("byte"));
        return FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"name\":\"order\",\"amount\":10,\"token\":\"dGVzdA==\"}")
                .requestContentTypes(List.of("application/json; charset=utf-8"))
                .headers(Set.of(CatsHeader.from("X-Keep", "1", false, null)))
                .allFieldsByHttpMethod(schemas.keySet())
                .requestPropertyTypes(schemas)
                .build();
    }
}
