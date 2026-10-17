package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@QuarkusTest
class NullElementsInArrayFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private NullElementsInArrayFieldsFuzzer nullElementsInArrayFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        nullElementsInArrayFieldsFuzzer = new NullElementsInArrayFieldsFuzzer(catsExecutor);
    }

    @Test
    void shouldRunIfFieldArrayAndSkipIfPrimitive() {
        Assertions.assertThat(nullElementsInArrayFieldsFuzzer.description()).isNotBlank();
        Assertions.assertThat(nullElementsInArrayFieldsFuzzer).hasToString("NullElementsInArrayFieldsFuzzer");
        Assertions.assertThat(nullElementsInArrayFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("arrayField"));
        Mockito.when(data.getPayload()).thenReturn("{\"arrayField\":[{\"inner\":\"innerValue\"}]}");

        nullElementsInArrayFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }
}
