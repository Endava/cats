package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

@QuarkusTest
class XmlContentTypeLinterTest {

    private TestCaseListener testCaseListener;
    private XmlContentTypeLinter xmlContentTypeContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        xmlContentTypeContractInfoFuzzer = new XmlContentTypeLinter(testCaseListener);
    }

    @Test
    void shouldReportErrorWhenRequestBodyIsXml() {
        FuzzingData data = FuzzingData.builder().path("/pet").requestContentTypes(Collections.singletonList("application/xml")).build();
        xmlContentTypeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.contains("Path accepts [application/xml] as Content-Type"));
    }

    @Test
    void shouldReportInfoWhenRequestBodyIsJson() {
        FuzzingData data = FuzzingData.builder().path("/pet").requestContentTypes(Collections.singletonList("application/json")).build();
        xmlContentTypeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.contains("Path does not accept [application/xml] as Content-Type"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(xmlContentTypeContractInfoFuzzer).hasToString(xmlContentTypeContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(xmlContentTypeContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI contract paths responses and requests does not offer `application/xml` as a Content-Type");
    }
}
