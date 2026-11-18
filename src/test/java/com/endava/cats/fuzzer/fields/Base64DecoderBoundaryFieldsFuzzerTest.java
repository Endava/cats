package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class Base64DecoderBoundaryFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener listener;
    private FilesArguments files;
    private FieldsIteratorExecutor executor;
    private Base64DecoderBoundaryFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        files = Mockito.mock(FilesArguments.class);
        Mockito.doAnswer(Base64DecoderBoundaryFieldsFuzzerTest::executeTest)
                .when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        executor = new FieldsIteratorExecutor(serviceCaller, listener, Mockito.mock(MatchArguments.class), files);
        fuzzer = new Base64DecoderBoundaryFieldsFuzzer(executor);
    }

    private static Object executeTest(InvocationOnMock invocation) {
        invocation.<Runnable>getArgument(2).run();
        return null;
    }

    @ParameterizedTest
    @CsvSource({"POST,byte,attachment", "GET,,next_cursor", "POST,,next_cursor", "POST,byte,next_cursor"})
    void shouldSendExactlyTwelveUnmodifiedValuesPerMatchingField(HttpMethod method, String format, String field) {
        Schema<String> schema = new Schema<String>().type("string").format(format);
        FuzzingData data = data(JsonUtils.GSON.toJson(Map.of(field, "original", "unchanged", "keep")), Map.of(field, schema), method);

        fuzzer.fuzz(data);

        List<ServiceData> requests = requests(12);
        Assertions.assertThat(requests).extracting(request -> fieldValue(request, field)).containsExactlyElementsOf(strings(Base64DecoderInputs.boundaryValues()));
        Assertions.assertThat(requests).extracting(request -> fieldValue(request, "unchanged")).containsOnly("keep");
        Assertions.assertThat(requests).extracting(ServiceData::isReplaceRefData).containsOnly(true);
        Assertions.assertThat(requests).extracting(ServiceData::getMutationTargets)
                .containsOnly(Set.of(method == HttpMethod.GET ? RequestTarget.query(field) : RequestTarget.body(field)));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 400, 401, 422, 500, 502})
    void shouldUseExistingResponseReportingForEveryStatus(int status) {
        CatsResponse response = CatsResponse.builder().responseCode(status).body("{}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        FuzzingData data = data("{\"attachment\":\"e30=\"}", Map.of("attachment", new Schema<>().type("string").format("byte")), HttpMethod.POST);

        fuzzer.fuzz(data);

        Mockito.verify(listener, Mockito.times(12)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.same(response),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX));
        Mockito.verify(listener, Mockito.never()).addConfidence(Mockito.any());
    }

    @Test
    void shouldKeepCursorStructureAndBoundaryRequestsDisjoint() {
        FuzzingData data = data("{\"next_cursor\":\"e30=\"}", Map.of("next_cursor", new Schema<>().type("string")), HttpMethod.GET);

        new CursorAndPaginationTokenFieldsFuzzer(executor).fuzz(data);
        fuzzer.fuzz(data);

        Assertions.assertThat(requests(16)).extracting(request -> fieldValue(request, "next_cursor"))
                .doesNotHaveDuplicates().containsAll(strings(Base64DecoderInputs.cursorValues())).containsAll(strings(Base64DecoderInputs.boundaryValues()));
    }

    @Test
    void shouldSkipUnrelatedBinaryEnumNonStringAndAbsentFields() {
        Schema<String> enumSchema = new Schema<String>().type("string").format("byte");
        enumSchema.setEnum(List.of("e30="));
        Map<String, Schema> schemas = Map.of("token", new Schema<>().type("string"), "file", new Schema<>().type("string").format("binary"),
                "enum", enumSchema, "cursor", new Schema<>().type("integer"), "absent", new Schema<>().type("string").format("byte"));
        FuzzingData data = data("{\"token\":\"t\",\"file\":\"f\",\"enum\":\"e30=\",\"cursor\":5}", schemas, HttpMethod.POST);

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldHonorFieldsRemovedByReferenceData() {
        Mockito.when(files.getRefData("/encoded")).thenReturn(Map.of("attachment", ServiceCaller.CATS_REMOVE_FIELD));
        FuzzingData data = data("{\"attachment\":\"e30=\"}", Map.of("attachment", new Schema<>().type("string").format("byte")), HttpMethod.POST);

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldHonorExecutionFiltering() {
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(false);
        FuzzingData data = data("{\"attachment\":\"e30=\"}", Map.of("attachment", new Schema<>().type("string").format("byte")), HttpMethod.POST);

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldDescribeTheFuzzerAndSupportQueryMethods() {
        Assertions.assertThat(fuzzer.description()).contains("Base64", "padding");
        Assertions.assertThat(fuzzer).hasToString("Base64DecoderBoundaryFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).isEmpty();
    }

    private List<ServiceData> requests(int count) {
        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(count)).call(captor.capture());
        return captor.getAllValues();
    }

    private static String fieldValue(ServiceData request, String field) {
        return JsonUtils.parseAsJsonElement(request.getPayload()).getAsJsonObject().get(field).getAsString();
    }

    private static List<String> strings(List<Object> values) {
        return values.stream().map(Object::toString).toList();
    }

    private static FuzzingData data(String payload, Map<String, Schema> schemas, HttpMethod method) {
        return FuzzingData.builder().path("/encoded").method(method).reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json")).payload(payload)
                .allFieldsByHttpMethod(schemas.keySet()).requestPropertyTypes(schemas).build();
    }
}
