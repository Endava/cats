package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
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
class HomoglyphEnumFieldsFuzzerTest {
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private HomoglyphEnumFieldsFuzzer homoglyphEnumFieldsFuzzer;

    @BeforeEach
    void setup() {
        catsExecutor = new FieldsIteratorExecutor(Mockito.mock(ServiceCaller.class), testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        homoglyphEnumFieldsFuzzer = new HomoglyphEnumFieldsFuzzer(catsExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(homoglyphEnumFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(homoglyphEnumFieldsFuzzer).hasToString("HomoglyphEnumFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(homoglyphEnumFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSkipIfFieldNotEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("nonEnumField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("nonEnumField", new Schema()));

        Mockito.when(data.getPayload()).thenReturn("""
                    {"nonEnumField": "value"}
                """);
        homoglyphEnumFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipIfFieldNotInJson() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("nonEnumField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("nonEnumField", new Schema()._enum(List.of("value"))));

        Mockito.when(data.getPayload()).thenReturn("""
                    {"anotherFieldHere": "value"}
                """);
        homoglyphEnumFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunIfFieldEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("enumField"));

        Schema<String> schema = new Schema<>();
        schema.setEnum(List.of("originalValue", "mutatedValue"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("enumField", schema));

        Mockito.when(data.getPayload()).thenReturn("""
                   {"enumField": "originalValue"}
                """);

        homoglyphEnumFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(6)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
    }
}
