package com.endava.cats.fuzzer.fields;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
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
class DefaultValuesInFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    @InjectSpy
    CatsUtil catsUtil;
    private DefaultValuesInFieldsFuzzer defaultValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        defaultValuesInFieldsFuzzer = new DefaultValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(defaultValuesInFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(defaultValuesInFieldsFuzzer).hasToString("DefaultValuesInFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(defaultValuesInFieldsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldSkipIfEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myField"));
        Schema<String> myEnumSchema = new Schema<>();
        myEnumSchema.setEnum(List.of("one", "two"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", myEnumSchema));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        defaultValuesInFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipIfDiscriminator() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("myField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", new Schema<String>()));
        Mockito.when(testCaseListener.isFieldNotADiscriminator("myField")).thenReturn(false);
        Mockito.when(data.getPayload()).thenReturn("""
                    {"myField": 3}
                """);
        defaultValuesInFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void shouldExecuteWhenHavingDefaultAndNoDiscriminatorOrEnum() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Schema<String> mySchema = new Schema<>();
        mySchema.setDefault("test");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("objectField#myField", mySchema));
        Mockito.when(testCaseListener.isFieldNotADiscriminator("objectField#myField")).thenReturn(true);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("objectField#myField"));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"objectField": {
                            "myField": "innerValue"
                        }
                    }
                """);
        defaultValuesInFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }
}
