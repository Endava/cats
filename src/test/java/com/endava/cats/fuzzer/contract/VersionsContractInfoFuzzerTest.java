package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseExporterHtmlJs;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import javax.enterprise.inject.Instance;
import java.util.stream.Stream;

@QuarkusTest
class VersionsContractInfoFuzzerTest {
    private TestCaseListener testCaseListener;
    private VersionsContractInfoFuzzer versionsContractInfoFuzzer;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        TestCaseExporter exporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        versionsContractInfoFuzzer = new VersionsContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"/path/{version}/user", "/path/v1/user", "/path/version1.3/user"})
    void shouldReportError(String path) {
        FuzzingData data = FuzzingData.builder().path(path).build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("Path contains versioning information"));
    }

    @Test
    void shouldReportInfo() {
        FuzzingData data = FuzzingData.builder().path("/path/user").build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path does not contain versioning information"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() {
        FuzzingData data = FuzzingData.builder().path("/path/user").build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path does not contain versioning information"));

        Mockito.reset(testCaseListener);
        versionsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path does not contain versioning information"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(versionsContractInfoFuzzer).hasToString(versionsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(versionsContractInfoFuzzer.description()).isEqualTo("verifies that a given path doesn't contain versioning information");
    }

}
