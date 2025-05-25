package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

@QuarkusTest
class HeadersCaseLinterTest {

    private TestCaseListener testCaseListener;
    private HeadersCaseLinter headersCaseLinterFuzzer;

    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(Mockito.mock(TestCaseExporter.class)));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        headersCaseLinterFuzzer = new HeadersCaseLinter(testCaseListener, namingArguments);
        ReflectionTestUtils.setField(namingArguments, "headersNaming", NamingArguments.Naming.HTTP_HEADER);

    }

    @ParameterizedTest
    @CsvSource({"first_payload,SNAKE", "SecondPayload,PASCAL", "third-payload,KEBAB", "X-Rate,HTTP_HEADER"})
    void shouldMatchHeadersNamingStandards(String schemaName, NamingArguments.Naming naming) {
        ReflectionTestUtils.setField(namingArguments, "headersNaming", naming);
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData(schemaName, "200");
        data.getHeaders().add(CatsHeader.builder().name(schemaName).value(schemaName).build());

        headersCaseLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Headers follow naming conventions."));
    }

    @ParameterizedTest
    @CsvSource({"first-payload,SNAKE", "SecondPayload_2,PASCAL", "third_payload,KEBAB", "x_rate,HTTP_HEADER"})
    void shouldNotMatchHeadersNamingStandards(String headerName, NamingArguments.Naming naming) {
        ReflectionTestUtils.setField(namingArguments, "headersNaming", naming);
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData(headerName, "200");
        data.getHeaders().add(CatsHeader.builder().name(headerName).value(headerName).build());

        headersCaseLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Headers not following recommended naming"), Mockito.eq("Headers not following {} naming: {}"), Mockito.any(), Mockito.any());
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(headersCaseLinterFuzzer).hasToString(headersCaseLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(headersCaseLinterFuzzer.description()).isEqualTo("verifies that HTTP headers follow naming conventions");
    }
}
