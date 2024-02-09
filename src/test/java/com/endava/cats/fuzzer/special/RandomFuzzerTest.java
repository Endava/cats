package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@QuarkusTest
class RandomFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private TestCaseListener testCaseListener;
    private ExecutionStatisticsListener executionStatisticsListener;
    private MatchArguments matchArguments;
    private StopArguments stopArguments;
    private ReportingArguments reportingArguments;
    private RandomFuzzer randomFuzzer;
    @Inject
    Instance<Mutator> mutators;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        stopArguments = Mockito.mock(StopArguments.class);
        reportingArguments = Mockito.mock(ReportingArguments.class);
        executionStatisticsListener = Mockito.mock(ExecutionStatisticsListener.class);
        matchArguments = Mockito.mock(MatchArguments.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);

        randomFuzzer = new RandomFuzzer(simpleExecutor, testCaseListener,
                executionStatisticsListener,
                matchArguments, mutators,
                stopArguments, reportingArguments, Mockito.mock(FilesArguments.class), Mockito.mock(CatsUtil.class));
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunWhenEmptyPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        randomFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldRunWhenPayloadNotEmpty() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"id\":\"value\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("id"));
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong())).thenReturn(true);
        randomFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunForMultipleTimes() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"id\":\"value\"}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/path");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("id"));
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(false).thenReturn(false).thenReturn(true);
        Mockito.when(reportingArguments.isSummaryInConsole()).thenReturn(true);
        randomFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
    }

    @Test
    void shouldReportError() {
        CatsResponse response = CatsResponse.empty();
        FuzzingData data = FuzzingData.builder().build();
        Mockito.when(matchArguments.isMatchResponse(response)).thenReturn(true);
        Mockito.when(matchArguments.getMatchString()).thenReturn(" test");

        randomFuzzer.processResponse(response, data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultError(Mockito.any(), Mockito.eq(data),
                        Mockito.eq("Response matches arguments"), Mockito.eq("Response matches test"));
    }

    @Test
    void shouldReportSkip() {
        CatsResponse response = CatsResponse.empty();
        FuzzingData data = FuzzingData.builder().build();

        randomFuzzer.processResponse(response, data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .skipTest(Mockito.any(), Mockito.eq("Skipping test as response does not match given matchers!"));
    }

    @Test
    void shouldOverrideMethods() {
        Assertions.assertThat(randomFuzzer.description()).isNotBlank();
        Assertions.assertThat(randomFuzzer).hasToString("RandomFuzzer");
    }
}
