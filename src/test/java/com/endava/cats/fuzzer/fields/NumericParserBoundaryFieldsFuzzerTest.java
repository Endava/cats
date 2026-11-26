package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class NumericParserBoundaryFieldsFuzzerTest {
    private ServiceCaller caller;
    private TestCaseListener listener;
    private FilesArguments files;
    private NumericParserBoundaryFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        caller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        files = Mockito.mock(FilesArguments.class);
        Mockito.doAnswer(invocation -> { invocation.<Runnable>getArgument(2).run(); return null; })
                .when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(caller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        fuzzer = new NumericParserBoundaryFieldsFuzzer(new SimpleExecutor(listener, caller), files, new RobustnessResponseReporter(listener));
    }

    @Test
    void shouldPreserveRawAndQuotedNumberLexemesWithoutChangingOtherFields() {
        var data = data("{\"amount\":1,\"keep\":\"unchanged\"}", new Schema<>().type("object"), Map.of("amount", new Schema<>().type("number")));
        fuzzer.fuzz(data);
        var requests = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(38)).call(requests.capture());
        assertThat(requests.getAllValues()).extracting(ServiceData::getPayload).containsExactlyElementsOf(NumericParserInputs.values().stream()
                .map(value -> "{\"amount\":" + value + ",\"keep\":\"unchanged\"}").toList());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.isValidJson()).isFalse();
            assertThat(request.isReplaceRefData()).isFalse();
            assertThat(request.getMutationTargets()).containsExactly(RequestTarget.body("amount"));
        });
        Mockito.verify(listener, Mockito.never()).addConfidence(Mockito.any());
    }

    @Test
    void shouldSupportNumericRoots() {
        fuzzer.fuzz(data("1", new Schema<>().type("number"), Map.of()));
        var requests = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(38)).call(requests.capture());
        assertThat(requests.getAllValues()).extracting(ServiceData::getPayload).containsExactlyElementsOf(NumericParserInputs.values());
        assertThat(requests.getAllValues().getFirst().getMutationTargets()).containsExactly(RequestTarget.requestBody());
    }

    @Test
    void shouldSkipEnumsMissingFieldsAndRefDataExclusions() {
        Mockito.when(files.getRefData("/numbers")).thenReturn(Map.of("amount", com.endava.cats.io.ServiceCaller.CATS_REMOVE_FIELD));
        var enumSchema = new Schema<>().type("integer");
        enumSchema.setEnum(List.of(1, 2));
        fuzzer.fuzz(data("{\"amount\":1,\"choice\":1}", new Schema<>().type("object"),
                Map.of("amount", new Schema<>().type("number"), "choice", enumSchema, "missing", new Schema<>().type("number"))));
        Mockito.verifyNoInteractions(caller);
    }

    @Test
    void shouldSkipNonJsonAndFilteredExecution() {
        var data = data("{\"amount\":1}", new Schema<>().type("object"), Map.of("amount", new Schema<>().type("number")));
        fuzzer.fuzz(data.toBuilder().requestContentTypes(List.of("text/plain")).build());
        fuzzer.fuzz(data.toBuilder().method(HttpMethod.GET).build());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(false);
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(caller);
    }

    @Test
    void shouldPreApplyRefDataBeforeSubstitutingRawNumericLexemes() {
        var data = data("{\"amount\":1,\"apiKey\":\"RANDOM\"}", new Schema<>().type("object"),
                Map.of("amount", new Schema<>().type("number")));
        Mockito.when(caller.replacePayloadWithRefData(Mockito.any()))
                .thenReturn("{\"amount\":1,\"apiKey\":\"sk_sbox_secret\"}");

        fuzzer.fuzz(data);

        var requests = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(38)).call(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.getPayload()).contains("\"apiKey\":\"sk_sbox_secret\"");
            assertThat(request.isReplaceRefData()).isFalse();
            assertThat(request.isValidJson()).isFalse();
        });
    }

    @Test
    void shouldSkipFieldsRemovedByParentRefDataWithoutThrowing() {
        var data = data("{\"parent\":{\"amount\":1},\"other\":2}", new Schema<>().type("object"),
                Map.of("parent#amount", new Schema<>().type("number"), "other", new Schema<>().type("number")));
        Mockito.when(caller.replacePayloadWithRefData(Mockito.any()))
                .thenReturn("{\"other\":2}");

        fuzzer.fuzz(data);

        var requests = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(38)).call(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request ->
                assertThat(request.getMutationTargets()).containsExactly(RequestTarget.body("other")));
    }

    private static FuzzingData data(String payload, Schema<?> root, Map<String, Schema> properties) {
        return FuzzingData.builder().path("/numbers").method(HttpMethod.POST).reqSchema(root).payload(payload).headers(Set.of())
                .requestContentTypes(List.of("application/json")).allFieldsByHttpMethod(properties.keySet()).requestPropertyTypes(properties).build();
    }
}
