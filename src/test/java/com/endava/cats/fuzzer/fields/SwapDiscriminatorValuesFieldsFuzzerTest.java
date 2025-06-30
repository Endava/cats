package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class SwapDiscriminatorValuesFieldsFuzzerTest {
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private SwapDiscriminatorValuesFieldsFuzzer swapDiscriminatorValuesFieldsFuzzer;

    @Inject
    CatsGlobalContext catsGlobalContext;

    @BeforeEach
    void setup() {
        catsExecutor = new FieldsIteratorExecutor(Mockito.mock(ServiceCaller.class), testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        swapDiscriminatorValuesFieldsFuzzer = new SwapDiscriminatorValuesFieldsFuzzer(catsExecutor, catsGlobalContext);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(swapDiscriminatorValuesFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(swapDiscriminatorValuesFieldsFuzzer).hasToString("SwapDiscriminatorValuesFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(swapDiscriminatorValuesFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSkipIfFieldNotDiscriminator() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("nonDiscriminatorField"));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"nonDiscriminatorField": "value"}
                """);
        swapDiscriminatorValuesFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunIfFieldDiscriminator() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("discriminatorField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("discriminatorField", new Schema()));

        Mockito.when(data.getPayload()).thenReturn("""
                   {"discriminatorField": "oldValue"}
                """);
        catsGlobalContext.recordDiscriminator("", new Discriminator().propertyName("discriminatorField"), List.of("oldValue", "newValue"));

        swapDiscriminatorValuesFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
    }
}
