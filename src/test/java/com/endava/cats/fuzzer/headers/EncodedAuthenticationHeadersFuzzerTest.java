package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EncodedAuthenticationHeadersFuzzerTest {
    private ServiceCaller caller;
    private TestCaseListener listener;
    private FilesArguments files;
    private AuthArguments auth;
    private EncodedAuthenticationHeadersFuzzer fuzzer;

    @BeforeEach
    void setup() {
        caller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        files = Mockito.mock(FilesArguments.class);
        auth = Mockito.mock(AuthArguments.class);
        Mockito.doAnswer(invocation -> { invocation.<Runnable>getArgument(2).run(); return null; })
                .when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(caller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(401).body("{}").build());
        fuzzer = new EncodedAuthenticationHeadersFuzzer(new SimpleExecutor(listener, caller), auth, files, new RobustnessResponseReporter(listener));
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "jwt"})
    void shouldSendBoundedDistinctEnvelopesOnceAndRetainOtherHeaders(String scheme) {
        var data = declared(scheme);
        fuzzer.fuzz(data);
        fuzzer.fuzz(data);
        var requests = ArgumentCaptor.forClass(ServiceData.class);
        int count = "basic".equals(scheme) ? 19 : 55;
        Mockito.verify(caller, Mockito.times(count)).call(requests.capture());
        assertThat(requests.getAllValues()).extracting(EncodedAuthenticationHeadersFuzzerTest::authorization)
                .doesNotHaveDuplicates().containsExactlyElementsOf(EncodedAuthenticationInputs.values(
                        "basic".equals(scheme) ? EncodedAuthenticationInputs.Scheme.BASIC : EncodedAuthenticationInputs.Scheme.JWT));
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.getMutationTargets()).containsExactly(RequestTarget.header("Authorization"));
            assertThat(request.getHeaders()).anyMatch(header -> "X-Keep".equals(header.getName()) && "unchanged".equals(header.getValue()));
        });
    }

    @Test
    void shouldDiscoverConfiguredBasicAndJwtWithoutAnOpenApiSecurityDeclaration() {
        Mockito.when(auth.isBasicAuthSupplied()).thenReturn(true);
        Mockito.when(files.getHeaders("/auth")).thenReturn(Map.of("authorization", "Bearer e30.e30.c2ln"));
        fuzzer.fuzz(data());
        Mockito.verify(caller, Mockito.times(74)).call(Mockito.any());
    }

    @Test
    void shouldRespectAnonymousOperationOverridesAndSkipOpaqueBearerTokens() {
        var data = declared("basic").toBuilder().pathItem(new PathItem().get(new Operation().security(List.of()))).build();
        fuzzer.fuzz(data);
        Mockito.when(files.getHeaders("/auth")).thenReturn(Map.of("Authorization", "Bearer opaque-token"));
        fuzzer.fuzz(data());
        Mockito.verifyNoInteractions(caller);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 302, 400, 401, 403, 500, 502, 953})
    void shouldReportObservedFailuresWithoutPromotingAuthenticationOrSecurityConfidence(int status) {
        var data = declared("basic");
        var response = CatsResponse.builder().responseCode(status).body("{}").build();
        Mockito.when(caller.call(Mockito.any())).thenReturn(response);
        fuzzer.fuzz(data);
        if (status >= 200 && status < 500) {
            Mockito.verify(listener, Mockito.times(19)).reportResultInfo(Mockito.any(), Mockito.same(data), Mockito.anyString(), Mockito.eq(status));
        } else {
            Mockito.verify(listener, Mockito.times(19)).reportResult(Mockito.any(), Mockito.same(data), Mockito.same(response),
                    Mockito.eq(new ResponseCodeFamilyDynamic(List.of("2XX", "3XX", "4XX"))), Mockito.eq(false), Mockito.eq(false));
        }
        Mockito.verify(listener, Mockito.never()).addConfidence(Mockito.any());
    }

    @Test
    void shouldHonorExecutionFilters() {
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(false);
        fuzzer.fuzz(declared("basic"));
        Mockito.verifyNoInteractions(caller);
        assertThat(fuzzer.description()).contains("decoder");
    }

    private static String authorization(ServiceData data) {
        return data.getHeaders().stream().filter(header -> "Authorization".equalsIgnoreCase(header.getName()))
                .map(CatsHeader::getValue).findFirst().orElseThrow();
    }

    private static FuzzingData data() {
        return FuzzingData.builder().path("/auth").method(HttpMethod.GET).payload("{}").requestContentTypes(List.of("application/json"))
                .headers(Set.of(CatsHeader.from("X-Keep", "unchanged", false, null))).build();
    }

    private static FuzzingData declared(String kind) {
        var scheme = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("jwt".equals(kind) ? "bearer" : "basic");
        if ("jwt".equals(kind)) {
            scheme.setBearerFormat("JWT");
        }
        var api = new OpenAPI().components(new Components().addSecuritySchemes("auth", scheme))
                .security(List.of(new SecurityRequirement().addList("auth")));
        return data().toBuilder().openApi(api).build();
    }
}
