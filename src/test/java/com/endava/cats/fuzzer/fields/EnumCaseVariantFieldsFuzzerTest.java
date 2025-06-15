package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
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
class EnumCaseVariantFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private EnumCaseVariantFieldsFuzzer enumCaseVariantFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        enumCaseVariantFieldsFuzzer = new EnumCaseVariantFieldsFuzzer(catsExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(enumCaseVariantFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(enumCaseVariantFieldsFuzzer).hasToString("EnumCaseVariantFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(enumCaseVariantFieldsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldSkipIfNotEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", new Schema<String>()));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        enumCaseVariantFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipIfEnumAndDiscriminator() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myField"));
        Schema<String> myEnumSchema = new Schema<>();
        myEnumSchema.setEnum(List.of("one", "two"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", myEnumSchema));
        Mockito.when(testCaseListener.isFieldNotADiscriminator("myField")).thenReturn(false);
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        enumCaseVariantFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
        Mockito.verify(testCaseListener, Mockito.times(2)).isFieldNotADiscriminator("myField");
        Mockito.verifyNoMoreInteractions(testCaseListener);
    }

    @Test
    void shouldSkipWhenGeneratedCaseIsAlreadyInEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myField"));
        Schema<String> myEnumSchema = new Schema<>();
        myEnumSchema.setEnum(List.of("aa", "aA", "AA", "Aa"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", myEnumSchema));
        Mockito.when(testCaseListener.isFieldNotADiscriminator("myField")).thenReturn(true);
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        enumCaseVariantFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).isFieldNotADiscriminator("myField");
        Mockito.verifyNoMoreInteractions(testCaseListener);
    }

    @Test
    void shouldReplaceIfFieldEnumAndNotDiscriminator() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Schema<String> myEnumSchema = new Schema<>();
        myEnumSchema.setEnum(List.of("one", "two", "three"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("objectField#myField", myEnumSchema));
        Mockito.when(testCaseListener.isFieldNotADiscriminator("objectField#myField")).thenReturn(true);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("objectField#myField"));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"objectField": {
                            "myField": "innerValue"
                        }
                    }
                """);
        enumCaseVariantFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
    }
}
