package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class BidirectionalOverrideFieldsFuzzerTest {
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FieldsIteratorExecutor catsExecutor;
    private BidirectionalOverrideFieldsFuzzer bidirectionalOverrideFieldsFuzzer;

    @BeforeEach
    void setup() {
        catsExecutor = new FieldsIteratorExecutor(Mockito.mock(ServiceCaller.class), testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        bidirectionalOverrideFieldsFuzzer = new BidirectionalOverrideFieldsFuzzer(catsExecutor, Mockito.mock(ProcessingArguments.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(bidirectionalOverrideFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(bidirectionalOverrideFieldsFuzzer).hasToString("BidirectionalOverrideFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(bidirectionalOverrideFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSkipIfFieldNotString() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("nonStringField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("nonStringField", new Schema()));

        Mockito.when(data.getPayload()).thenReturn("""
                    {"nonStringField": "value"}
                """);
        bidirectionalOverrideFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipIfFieldEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("nonStringField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("nonStringField",
                new Schema().type("string")._enum(List.of("value1", "value2"))));

        Mockito.when(data.getPayload()).thenReturn("""
                    {"nonStringField": "value"}
                """);
        bidirectionalOverrideFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipIfFieldNotInJson() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("nonStringField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("nonStringField", new Schema().type("string")));

        Mockito.when(data.getPayload()).thenReturn("""
                    {"anotherFieldHere": "value"}
                """);
        bidirectionalOverrideFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunIfFieldString() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("stringField"));

        Schema<String> schema = new Schema<>();
        schema.setType("string");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("stringField", schema));

        Mockito.when(data.getPayload()).thenReturn("""
                   {"stringField": "originalValue"}
                """);

        bidirectionalOverrideFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
    }
}
