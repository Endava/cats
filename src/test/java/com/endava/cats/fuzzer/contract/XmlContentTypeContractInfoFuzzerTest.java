package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
class XmlContentTypeContractInfoFuzzerTest {


    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private IgnoreArguments ignoreArguments;

    @MockBean
    private TestCaseExporter testCaseExporter;

    private XmlContentTypeContractInfoFuzzer xmlContentTypeContractInfoFuzzer;

    @BeforeEach
    void setup() {
        xmlContentTypeContractInfoFuzzer = new XmlContentTypeContractInfoFuzzer(testCaseListener);
    }

    @Test
    void shouldReportErrorWhenRequestBodyIsXml() {
        FuzzingData data = FuzzingData.builder().path("/pet").requestContentTypes(Collections.singletonList("application/xml")).build();
        xmlContentTypeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("Path accepts [application/xml] as Content-Type]"));
    }

    @Test
    void shouldReportInfoWhenRequestBodyIsJson() {
        FuzzingData data = FuzzingData.builder().path("/pet").requestContentTypes(Collections.singletonList("application/json")).build();
        xmlContentTypeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.contains("Path does not accept [application/xml] as Content-Type"));
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
