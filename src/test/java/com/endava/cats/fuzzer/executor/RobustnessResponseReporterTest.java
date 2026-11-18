package com.endava.cats.fuzzer.executor;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

@QuarkusTest
class RobustnessResponseReporterTest {
    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 302, 304, 400, 401, 403, 413, 422, 429})
    void shouldAcceptControlledResponsesWithoutClaimingContractConformance(int code) {
        TestCaseListener listener = Mockito.mock(TestCaseListener.class);
        var logger = Mockito.mock(PrettyLogger.class);
        var data = FuzzingData.builder().responseCodes(java.util.Set.of("200")).build();
        var response = CatsResponse.builder().responseCode(code).build();
        new RobustnessResponseReporter(listener).report(logger, data, response);
        Mockito.verify(listener).reportResultInfo(Mockito.same(logger), Mockito.same(data), Mockito.contains("no server failure observed"), Mockito.eq(code));
        Mockito.verify(listener, Mockito.never()).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 501, 502, 503, 952, 953, 959})
    void shouldKeepExistingServerErrorAndTransportReporting(int code) {
        TestCaseListener listener = Mockito.mock(TestCaseListener.class);
        var response = CatsResponse.builder().responseCode(code).build();
        new RobustnessResponseReporter(listener).report(null, null, response);
        Mockito.verify(listener).reportResult(Mockito.isNull(), Mockito.isNull(), Mockito.same(response), Mockito.any(), Mockito.eq(false), Mockito.eq(false));
        Mockito.verify(listener, Mockito.never()).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldKeepCaptureAndThrottlingLimitsInconclusive() {
        TestCaseListener listener = Mockito.mock(TestCaseListener.class);
        var reporter = new RobustnessResponseReporter(listener);
        reporter.report(null, null, CatsResponse.builder().responseCode(200).bodyTruncated(true).build());
        reporter.report(null, null, CatsResponse.builder().responseCode(429).build().withRateLimited(3));
        Mockito.verify(listener, Mockito.times(2)).reportResult(Mockito.isNull(), Mockito.isNull(), Mockito.any(), Mockito.any(), Mockito.eq(false), Mockito.eq(false));
        Mockito.verify(listener, Mockito.never()).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any());
    }
}
