package com.endava.cats.fuzzer.headers;


import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class LargeNumberOfRandomHeadersFuzzerTest {

    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private LargeNumberOfRandomHeadersFuzzer largeNumberOfRandomHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        largeNumberOfRandomHeadersFuzzer = new LargeNumberOfRandomHeadersFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(largeNumberOfRandomHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldReturnRandomHeaderValues() {
        Assertions.assertThat(largeNumberOfRandomHeadersFuzzer.randomHeadersValueFunction().apply(10)).isNotBlank();
    }
}
