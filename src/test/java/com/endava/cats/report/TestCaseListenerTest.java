package com.endava.cats.report;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.exception.CatsException;
import com.endava.cats.exception.CatsExecutionCancelledException;
import com.endava.cats.exception.CatsExecutionLimitReachedException;
import com.endava.cats.execution.ExecutionSummaryProvider;
import com.endava.cats.execution.ExecutionStopController;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.http.RandomResourcesFuzzer;
import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsConfiguration;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseExecutionSummary;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.ExpectedResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.model.SecurityFinding;
import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.event.CatsExecutionEventPublisher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@QuarkusTest
class TestCaseListenerTest {

    TestCaseListener testCaseListener;

    ExecutionStatisticsListener executionStatisticsListener;
    IgnoreArguments ignoreArguments;
    ReportingArguments reportingArguments;
    FilterArguments filterArguments;
    @Inject
    CatsGlobalContext catsGlobalContext;
    @Inject
    ProcessingArguments processingArguments;

    private PrettyLogger logger;
    private Fuzzer fuzzer;
    private TestReportsGenerator testReportsGenerator;
    private CatsExecutionEventPublisher executionEventPublisher;
    private ExecutionStopController executionStopController;
    private ExecutionSummaryProvider executionSummaryProvider;


    @BeforeEach
    void setup() {
        logger = Mockito.mock(PrettyLogger.class);
        fuzzer = Mockito.mock(Fuzzer.class);
        reportingArguments = Mockito.mock(ReportingArguments.class);
        testReportsGenerator = Mockito.mock(TestReportsGenerator.class);
        executionSummaryProvider = Mockito.mock(ExecutionSummaryProvider.class);
        Mockito.when(executionSummaryProvider.snapshot()).thenReturn(new ExecutionSummary(
                0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), true, "", RunOutcome.completed()));
        Mockito.when(reportingArguments.getReportFormat()).thenReturn(List.of(ReportingArguments.ReportFormat.HTML_JS));
        executionStatisticsListener = Mockito.mock(ExecutionStatisticsListener.class);
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        executionEventPublisher = new CatsExecutionEventPublisher();
        executionStopController = Mockito.mock(ExecutionStopController.class);
        testCaseListener = new TestCaseListener(catsGlobalContext, executionStatisticsListener, testReportsGenerator,
                ignoreArguments, reportingArguments, filterArguments, executionEventPublisher, executionStopController,
                executionSummaryProvider);
        ReflectionTestUtils.setField(testCaseListener, "reportingInitialized", true);
        catsGlobalContext.getDiscriminators().clear();
        catsGlobalContext.getFuzzersConfiguration().clear();
    }

    @AfterEach
    void tearDown() {
        TestCaseListener.TEST.set(0);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void shouldRetainConfirmedSecurityErrorsUsingTheActiveFuzzerBeforeEndSession(boolean skipIgnored) {
        configureBlackbox(skipIgnored, List.of());
        executeBlackboxFinding(BypassAuthenticationFuzzer.class, CatsResponse.builder().responseCode(200).body("{}").build(), () -> {
            testCaseListener.addConfidence(FindingConfidence.CONFIRMED);
            testCaseListener.reportResultError(logger, null, "Unexpected response code: 200", "Synthetic confirmed finding");
        });

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement().satisfies(summary -> {
            Assertions.assertThat(summary.getError()).isTrue();
            Assertions.assertThat(summary.getFuzzer()).isEqualTo("BypassAuthentication");
        });
        Mockito.verify(executionStatisticsListener).increaseErrors("/items");
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shouldKeepUnassessedSecurityErrorsOnlyOutsideBlackbox(boolean blackbox, boolean skipIgnored) {
        configureBlackbox(skipIgnored, List.of());
        IgnoreArguments arguments = (IgnoreArguments) ReflectionTestUtils.getField(testCaseListener, "ignoreArguments");
        ReflectionTestUtils.setField(arguments, "blackbox", blackbox);
        executeBlackboxFinding(BypassAuthenticationFuzzer.class, CatsResponse.builder().responseCode(200).body("{}").build(), () ->
                testCaseListener.reportResultError(logger, null, "Unexpected response code: 200", "Anonymous request succeeded"));

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).hasSize(blackbox && skipIgnored ? 0 : 1);
        testCaseListener.testCaseSummaryDetails.forEach(summary -> {
            Assertions.assertThat(summary.getError()).isEqualTo(!blackbox);
            Assertions.assertThat(summary.getConfidence()).isNull();
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(blackbox ? 0 : 1)).increaseErrors("/items");
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void shouldLetLiteralIgnoreCodesOverrideNewBlackboxSecurityInclusions(boolean skipIgnored) {
        configureBlackbox(skipIgnored, List.of("2xx"));
        executeBlackboxFinding(BypassAuthenticationFuzzer.class, CatsResponse.builder().responseCode(200).body("{}").build(), () -> {
            testCaseListener.addConfidence(FindingConfidence.CONFIRMED);
            testCaseListener.testCaseMap.get(MDC.get(TestCaseListener.ID)).setErrorLeaks(List.of("nullpointerexception"));
            testCaseListener.reportResultError(logger, null, "Unexpected response code: 200", "Synthetic confirmed finding");
        });

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).hasSize(skipIgnored ? 0 : 1);
        if (!skipIgnored) {
            Assertions.assertThat(testCaseListener.testCaseSummaryDetails.getFirst().getResult()).isEqualTo("success");
        }
    }

    @Test
    void shouldNotTreatStrictPromotedWarningsAsNativeErrorsInBlackbox() {
        configureBlackbox(false, List.of());
        NamingArguments naming = Mockito.mock(NamingArguments.class);
        Mockito.when(naming.isStrict()).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "namingArguments", naming);
        executeBlackboxFinding(BypassAuthenticationFuzzer.class, CatsResponse.builder().responseCode(200).body("{}").build(), () ->
                testCaseListener.reportResultWarn(logger, null, Reason.CROSS_PARENT_RESOURCE_EXPOSURE.value(), "Synthetic warning"));

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement()
                .satisfies(summary -> Assertions.assertThat(summary.getResult()).isEqualTo("success"));
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED,true,false", "FIRM,false,false", "TENTATIVE,false,false",
            "CONFIRMED,true,true", "FIRM,false,true", "TENTATIVE,false,true"})
    void shouldRespectSecurityConfidenceDespiteKeywordLeaksInBlackbox(FindingConfidence confidence, boolean reportable,
                                                                      boolean skipIgnored) {
        configureBlackbox(skipIgnored, List.of());
        CatsResponse response = CatsResponse.builder().responseCode(200).body("{}").build();
        executeBlackboxFinding(HappyPathFuzzer.class, response, () -> {
            testCaseListener.testCaseMap.get(MDC.get(TestCaseListener.ID)).setErrorLeaks(List.of("nullpointerexception"));
            testCaseListener.reportSecurityFinding(logger, null, response,
                    new SecurityFinding(Reason.SQL_INJECTION.value(), "Synthetic injection", confidence));
        });

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).hasSize(skipIgnored && !reportable ? 0 : 1);
        testCaseListener.testCaseSummaryDetails.forEach(summary -> {
            Assertions.assertThat(summary.getError()).isEqualTo(reportable);
            Assertions.assertThat(summary.getConfidence()).isEqualTo(confidence);
            Assertions.assertThat(summary.getResultReason()).isEqualTo(Reason.SQL_INJECTION.value());
        });
    }

    @ParameterizedTest
    @CsvSource({"101", "302", "501"})
    void shouldSuppressOrdinaryNonServerResponsesAnd501InBlackbox(int responseCode) {
        configureBlackbox(false, List.of());
        executeBlackboxFinding(HappyPathFuzzer.class, CatsResponse.builder().responseCode(responseCode).body("{}").build(), () ->
                testCaseListener.reportResultError(logger, null, "Unexpected response code: " + responseCode, "Synthetic outcome"));

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement()
                .satisfies(summary -> Assertions.assertThat(summary.getResult()).isEqualTo("success"));
    }

    @ParameterizedTest
    @CsvSource({"999,0", "200,20"})
    void shouldPreserveExistingExceptionAndTimeoutBypasses(int status, long responseTime) {
        configureBlackbox(true, List.of("2xx", "4xx", "9xx"));
        Mockito.when(reportingArguments.getMaxResponseTime()).thenReturn(10);
        CatsResponse response = CatsResponse.builder().responseCode(status).body("{}").responseTimeInMs(responseTime).build();
        executeBlackboxFinding(HappyPathFuzzer.class, response, () ->
                testCaseListener.reportResultError(logger, null, "Synthetic error", "Synthetic error"));

        Mockito.verify(executionStatisticsListener).increaseErrors("/items");
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement()
                .satisfies(summary -> Assertions.assertThat(summary.getError()).isTrue());
    }

    @ParameterizedTest
    @CsvSource({"200,ERROR,false", "400,WARN,false", "200,INFO,false", "501,INFO,false",
            "200,ERROR,true", "400,WARN,true", "200,INFO,true", "501,INFO,true",
            "500,ERROR,true", "502,WARN,true", "500,INFO,true"})
    void shouldKeepKeywordOnlyLeaksOutOfBlackboxUnlessTheServerFailed(int status, Level level, boolean skipIgnored) {
        configureBlackbox(skipIgnored, List.of());
        executeBlackboxFinding(HappyPathFuzzer.class, CatsResponse.builder().responseCode(status).body("nullpointerexception").build(), () -> {
            ReflectionTestUtils.invokeMethod(testCaseListener, "extractErrorLeaks");
            switch (level) {
                case ERROR -> testCaseListener.reportResultError(logger, null, "Synthetic error", "Synthetic error");
                case WARN -> testCaseListener.reportResultWarn(logger, null, "Synthetic warning", "Synthetic warning");
                default -> testCaseListener.reportResultInfo(logger, null, "Synthetic success");
            }
        });

        boolean serverError = status == 500 || status == 502;
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).hasSize(skipIgnored && !serverError ? 0 : 1);
        testCaseListener.testCaseSummaryDetails.forEach(summary -> Assertions.assertThat(summary.getError()).isEqualTo(serverError));
        Mockito.verify(executionStatisticsListener, Mockito.times(serverError ? 1 : 0)).increaseErrors("/items");
    }

    @Test
    void shouldDropOrdinarySuccessesWhenBlackboxAndSkipIgnoredAreEnabled() {
        configureBlackbox(true, List.of());
        executeBlackboxFinding(HappyPathFuzzer.class, CatsResponse.builder().responseCode(200).body("{}").build(), () ->
                testCaseListener.reportResultInfo(logger, null, "Synthetic success"));

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).isEmpty();
        Mockito.verify(executionStatisticsListener).increaseSkippedFromReporting("/items");
    }

    @Test
    void shouldDemoteRateLimitWarningsWithoutRedispatchingThemRecursively() {
        configureBlackbox(false, List.of());
        CatsResponse response = CatsResponse.from(429, "{}", "GET", 5).withRateLimited(2);
        executeBlackboxFinding(HappyPathFuzzer.class, response, () -> testCaseListener.reportRateLimitedResponse(logger, null, response));

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement()
                .satisfies(summary -> Assertions.assertThat(summary.getResult()).isEqualTo("success"));
        Mockito.verify(executionStatisticsListener).increaseSuccess("/items");
    }

    @Test
    void shouldDemoteTruncatedWarningsWithoutRedispatchingThemRecursively() {
        configureBlackbox(false, List.of());
        CatsResponse response = CatsResponse.builder().responseCode(200).body("partial").bodyTruncated(true)
                .capturedBodyBytes(1024).responseBodyLimit(1024).declaredContentLength(2048).build();
        executeBlackboxFinding(HappyPathFuzzer.class, response, () -> testCaseListener.reportTruncatedResponse(logger, null, response));

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement()
                .satisfies(summary -> Assertions.assertThat(summary.getResult()).isEqualTo("success"));
        Mockito.verify(executionStatisticsListener).increaseSuccess("/items");
    }

    @Test
    void shouldRetainKnownTransportErrorsUnderBlackbox() {
        configureBlackbox(true, List.of());
        CatsResponse response = CatsResponse.builder().responseCode(953).body("connection refused").build();
        executeBlackboxFinding(HappyPathFuzzer.class, response, () ->
                testCaseListener.reportResultError(logger, null, Reason.CONNECTION_REFUSED.value(), "Connection refused"));

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).singleElement()
                .satisfies(summary -> Assertions.assertThat(summary.getError()).isTrue());
    }

    private void configureBlackbox(boolean skipIgnored, List<String> ignoredCodes) {
        IgnoreArguments arguments = new IgnoreArguments();
        ReflectionTestUtils.setField(arguments, "blackbox", true);
        ReflectionTestUtils.setField(arguments, "skipReportingForIgnoredCodes", skipIgnored);
        ReflectionTestUtils.setField(arguments, "ignoreResponseCodes", ignoredCodes);
        ReflectionTestUtils.setField(testCaseListener, "ignoreArguments", arguments);
    }

    private void executeBlackboxFinding(Class<?> activeFuzzer, CatsResponse response, Runnable report) {
        testCaseListener.beforeFuzz(activeFuzzer, "/items", "GET");
        try {
            testCaseListener.createAndExecuteTest(logger, fuzzer, () -> reportBlackboxResponse(response, report), FuzzingData.builder().build());
        } finally {
            testCaseListener.afterFuzz("/items");
        }
    }

    private void reportBlackboxResponse(CatsResponse response, Runnable report) {
        testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").url("http://localhost/items").payload("{}").build());
        testCaseListener.addResponse(response);
        testCaseListener.addContractPath("/items");
        Assertions.assertThat(testCaseListener.testCaseMap.get(MDC.get(TestCaseListener.ID)).getFuzzer()).isNull();
        report.run();
    }

    @Test
    void givenAFunction_whenExecutingATestCase_thenTheCorrectContextIsCreatedAndTheTestCaseIsWrittenToFile() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
        }, FuzzingData.builder().build());

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails.getFirst()).isNotNull();
        Mockito.verify(testReportsGenerator).writeTestCase(Mockito.any());
        Mockito.verify(executionStopController).checkBeforeTest();
        Mockito.verify(executionStopController).checkAfterTest();
    }

    @Test
    void shouldRecordRuntimeResourceCorrelationOnTheActiveTestCase() {
        ResourceCorrelation correlation = ResourceCorrelation.builder()
                .sourceMethod("POST").sourcePath("/customers").sourceLocation("response.body.$.id")
                .target(RequestTarget.path("customerId")).value("customer-42").build();
        MDC.put(TestCaseListener.ID, "correlation-test");
        testCaseListener.testCaseMap.put("correlation-test", new CatsTestCase());
        try {
            testCaseListener.addRuntimeCorrelation(logger, correlation);

            Assertions.assertThat(testCaseListener.testCaseMap.get("correlation-test").getRuntimeCorrelations())
                    .containsExactly(correlation);
            Mockito.verify(logger).note(Mockito.contains("Runtime resource correlation"),
                    Mockito.eq("POST"), Mockito.eq("/customers"), Mockito.eq("response.body.$.id"),
                    Mockito.eq("path"), Mockito.eq("customerId"), Mockito.eq("customer-42"));
        } finally {
            MDC.remove(TestCaseListener.ID);
            testCaseListener.testCaseMap.clear();
        }
    }

    @Test
    void shouldRecordMutationTargetsOnTheActiveTestCase() {
        RequestTarget body = RequestTarget.body("customerId");
        RequestTarget header = RequestTarget.header("X-Test");
        MDC.put(TestCaseListener.ID, "mutation-test");
        testCaseListener.testCaseMap.put("mutation-test", new CatsTestCase());
        try {
            testCaseListener.addMutationTargets(List.of(body, header, body));

            Assertions.assertThat(testCaseListener.testCaseMap.get("mutation-test").getMutationTargets())
                    .containsExactly(body, header);
        } finally {
            MDC.remove(TestCaseListener.ID);
        }
    }

    @Test
    void shouldApplyExecutionLimitAfterPersistingTheCurrentTestCase() {
        CatsExecutionLimitReachedException limitReached =
                new CatsExecutionLimitReachedException("Test limit reached");
        Mockito.doThrow(limitReached).when(executionStopController).checkAfterTest();
        Runnable execution = () -> testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
        }, FuzzingData.builder().build());

        Assertions.assertThatThrownBy(execution::run)
                .isSameAs(limitReached);

        Mockito.verify(testReportsGenerator).writeTestCase(Mockito.any());
        Assertions.assertThat(testCaseListener.testCaseMap).isEmpty();
    }

    @Test
    void shouldCancelAfterPersistingTheCurrentTestCase() {
        FuzzingData fuzzingData = FuzzingData.builder().build();
        Runnable testExecution = () -> {
        };
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThatThrownBy(() ->
                    testCaseListener.createAndExecuteTest(logger, fuzzer, testExecution, fuzzingData))
                    .isInstanceOf(CatsExecutionCancelledException.class);

            Mockito.verify(testReportsGenerator).writeTestCase(Mockito.any());
            Assertions.assertThat(testCaseListener.testCaseMap).isEmpty();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldNotReportCancellationAsATestError() {
        try {
            Runnable testExecution = () -> {
                Thread.currentThread().interrupt();
                CatsExecutionCancelledException.check();
            };
            FuzzingData fuzzingData = FuzzingData.builder().build();
            Assertions.assertThatThrownBy(() -> testCaseListener.createAndExecuteTest(logger, fuzzer, testExecution, fuzzingData))
                    .isInstanceOf(CatsExecutionCancelledException.class);

            Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
            Assertions.assertThat(testCaseListener.testCaseMap).isEmpty();
            Assertions.assertThat(MDC.get(TestCaseListener.ID)).isNull();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldClearTestContextWhenFinalizationFails() {
        IllegalStateException reportFailure = new IllegalStateException("Cannot write test report");
        Mockito.doThrow(reportFailure).when(testReportsGenerator).writeTestCase(Mockito.any());

        Assertions.assertThatThrownBy(() -> testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
        }, FuzzingData.builder().build())).isSameAs(reportFailure);

        Assertions.assertThat(testCaseListener.testCaseMap).isEmpty();
        Assertions.assertThat(MDC.get(TestCaseListener.ID)).isNull();
    }

    @Test
    void shouldRecordOnlyActualResponsesIncludingThoseOmittedFromReports() {
        Mockito.when(ignoreArguments.isSkipReportingForSuccess()).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addResponse(CatsResponse.builder().responseCode(204).build());
            testCaseListener.reportInfo(logger, "Success");
        }, FuzzingData.builder().contractPath("/pets").build());
        testCaseListener.createAndExecuteTest(logger, fuzzer, () ->
                testCaseListener.reportInfo(logger, "Contract check passed"), FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener).recordResponseCode(204);
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).recordResponseCode(Mockito.anyInt());
    }

    @Test
    void shouldNotWriteRecordedErrorsToConsoleInTuiMode() {
        CatsGlobalContext localContext = Mockito.mock(CatsGlobalContext.class);
        ReportingArguments tuiArguments = Mockito.mock(ReportingArguments.class);
        Mockito.when(tuiArguments.isTui()).thenReturn(true);
        TestCaseListener listener = new TestCaseListener(localContext, executionStatisticsListener,
                testReportsGenerator, ignoreArguments, tuiArguments, filterArguments, executionEventPublisher,
                executionStopController, executionSummaryProvider);

        listener.endSession();

        Mockito.verify(localContext, Mockito.never()).writeRecordedErrorsIfPresent();
        Mockito.verify(executionStatisticsListener, Mockito.never()).getExecutionsPerPath("N/A");
    }

    @Test
    void shouldPreserveGlobalFuzzerStatisticsOutsideTuiSummaryMode() {
        Mockito.when(reportingArguments.isTui()).thenReturn(false);
        Mockito.when(reportingArguments.isSummaryInConsole()).thenReturn(false);

        testCaseListener.endSession();

        Mockito.verify(executionStatisticsListener).getExecutionsPerPath("N/A");
    }

    @Test
    void givenAFunction_whenExecutingATestCase_thenACompletedTestEventIsPublished() {
        List<CatsExecutionEvent> events = new ArrayList<>();
        executionEventPublisher.subscribe(events::add);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").headers(List.of()).build());
            testCaseListener.addResponse(CatsResponse.builder().responseCode(200).headers(List.of()).build());
            testCaseListener.reportInfo(logger, "Success");
        }, FuzzingData.builder().contractPath("/pets").method(HttpMethod.GET).build());

        Assertions.assertThat(events).filteredOn(CatsExecutionEvent.TestCompleted.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    CatsExecutionEvent.TestCompleted completed = (CatsExecutionEvent.TestCompleted) event;
                    Assertions.assertThat(completed.test().contractPath()).isEqualTo("/pets");
                    Assertions.assertThat(completed.test().request().httpMethod()).isEqualTo("GET");
                    Assertions.assertThat(completed.test().response().responseCode()).isEqualTo(200);
                });
        Mockito.verify(executionStatisticsListener).increaseProcessedTests();
    }

    @Test
    void shouldRecordAndPublishEveryRequestAttempt() {
        List<CatsExecutionEvent> events = new ArrayList<>();
        executionEventPublisher.subscribe(events::add);

        testCaseListener.recordRequestAttempt();

        Mockito.verify(executionStatisticsListener).increaseRequestsAttempted();
        Assertions.assertThat(events).singleElement().isInstanceOf(CatsExecutionEvent.RequestAttempted.class);
    }

    @Test
    void givenAFunction_whenExecutingATestCaseAndAddingDetails_thenTheDetailsAreCorrectlyAttachedToTheTestCase() {
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).isEmpty();

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addScenario(logger, "Given a {} field", "string");
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
            testCaseListener.addResponse(CatsResponse.builder().build());
            testCaseListener.addFullRequestPath("fullPath");
            testCaseListener.addContractPath("path");
            testCaseListener.addExpectedResult(logger, "Should return {}", "2XX");
            testCaseListener.reportWarn(logger, "Warn {} happened", "1");
        }, FuzzingData.builder().build());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase).isNotNull();
        Assertions.assertThat(testCase.getPath()).isEqualTo("path");
        Assertions.assertThat(testCase.getScenario()).isEqualTo("Given a string field");
    }

    @Test
    void givenATestCase_whenExecutingStartAndEndSession_thenTheSummaryAndReportFilesAreCreated() throws Exception {
        ReflectionTestUtils.setField(testCaseListener, "appName", "CATS");
        testCaseListener.startSession();
        testCaseListener.initReportingPath();
        testCaseListener.endSession();

        Mockito.verify(testReportsGenerator).initPath(null);
        Mockito.verify(testReportsGenerator, Mockito.times(1)).writeHelperFiles();
        Mockito.verify(testReportsGenerator, Mockito.times(1))
                .writeSummary(Mockito.anyList(), Mockito.any(ExecutionSummary.class), Mockito.anyList());
    }

    @Test
    void shouldClearListenerStateAtTheStartOfEachSession() {
        testCaseListener.testCaseMap.put("old", new CatsTestCase());
        testCaseListener.testCaseSummaryDetails.add(Mockito.mock(CatsTestCaseSummary.class));
        testCaseListener.testCaseExecutionDetails.add(Mockito.mock(CatsTestCaseExecutionSummary.class));
        TestCaseListener.TEST.set(42);
        MDC.put(TestCaseListener.ID, "old");

        testCaseListener.startSession();

        Assertions.assertThat(testCaseListener.testCaseMap).isEmpty();
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).isEmpty();
        Assertions.assertThat(testCaseListener.testCaseExecutionDetails).isEmpty();
        Assertions.assertThat(TestCaseListener.TEST).hasValue(0);
        Assertions.assertThat(MDC.get(TestCaseListener.ID)).isNull();
        Mockito.verify(executionStatisticsListener).startSession();
    }

    @Test
    void shouldEvaluatePreparedProblemsBeforeSnapshotAndShareThemWithTheReport(@TempDir Path directory) throws Exception {
        List<CatsTestCaseSummary> baseline = ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun());
        Path baselineFolder = directory.resolve("baseline");
        ProblemReportConsistencyTest.write(baseline, baselineFolder);
        QualityGateArguments gate = new QualityGateArguments();
        ReflectionTestUtils.setField(gate, "baseline", baselineFolder.toFile());
        Mockito.when(executionSummaryProvider.getQualityGateArguments()).thenReturn(gate);
        List<CatsTestCaseSummary> current = ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun());
        Assertions.assertThat(current).allMatch(test -> test.getProblemId() == null);
        testCaseListener.testCaseSummaryDetails.addAll(current);
        var previousSchema = catsGlobalContext.getOpenAPI();
        catsGlobalContext.setOpenAPI(ProblemReportFixtures.RISK_CONTRACT);
        try {
            Mockito.when(executionSummaryProvider.snapshot()).thenAnswer(_ -> {
                Assertions.assertThat(gate.getBaselineRegressionDetected()).isFalse();
                Assertions.assertThat(current).filteredOn(com.endava.cats.report.problem.ProblemCatalog::isReportable)
                        .allMatch(test -> test.getProblemId() != null);
                return new ExecutionStatisticsListener().snapshot(true, "baseline");
            });
            testCaseListener.endSession();
            var comparison = (com.endava.cats.command.model.ComparisonResult) ReflectionTestUtils.getField(testCaseListener, "lastBaselineComparison");
            Mockito.verify(testReportsGenerator).writeSummary(Mockito.same(testCaseListener.testCaseSummaryDetails), Mockito.any(),
                    Mockito.argThat(problems -> problems.stream().allMatch(problem -> comparison.unchangedOccurrences().stream()
                            .anyMatch(delta -> delta.problemId().equals(problem.id())))));
            Assertions.assertThat(comparison.regression()).isFalse();
        } finally {
            catsGlobalContext.setOpenAPI(previousSchema);
        }
    }

    @Test
    void shouldMarkSessionFailedWhenFinalReportGenerationFails() {
        ExecutionSummary completed = new ExecutionSummary(
                0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), true, "", RunOutcome.completed());
        ExecutionSummary failed = new ExecutionSummary(
                0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), true, "",
                RunOutcome.failed("Report generation failed: disk full"));
        Mockito.when(executionSummaryProvider.snapshot()).thenReturn(completed, failed);
        Mockito.doThrow(new IllegalStateException("disk full"))
                .when(testReportsGenerator).writeSummary(Mockito.anyList(), Mockito.any(), Mockito.anyList());

        ExecutionSummary result = testCaseListener.endSession();

        Assertions.assertThat(result).isSameAs(failed);
        Mockito.verify(executionStatisticsListener).markFailed("Report generation failed: disk full");
    }

    @Test
    void shouldRewriteSummaryWithFailedOutcomeWhenLateReportGenerationFails() {
        ExecutionSummary completed = new ExecutionSummary(
                0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), true, "", RunOutcome.completed());
        ExecutionSummary failed = new ExecutionSummary(
                0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), true, "",
                RunOutcome.failed("Report generation failed: console unavailable"));
        Mockito.when(executionSummaryProvider.snapshot()).thenReturn(completed, failed);
        Mockito.doThrow(new IllegalStateException("console unavailable"))
                .when(testReportsGenerator).printExecutionDetails(completed);

        ExecutionSummary result = testCaseListener.endSession();

        Assertions.assertThat(result).isSameAs(failed);
        Mockito.verify(testReportsGenerator).writeSummary(Mockito.eq(testCaseListener.testCaseSummaryDetails), Mockito.eq(completed), Mockito.anyList());
        Mockito.verify(testReportsGenerator).writeSummary(Mockito.eq(testCaseListener.testCaseSummaryDetails), Mockito.eq(failed), Mockito.anyList());
    }

    @Test
    void shouldPreserveOriginalOutcomeWhenReportingWasNotInitialized() {
        ExecutionSummary failed = new ExecutionSummary(
                0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), true, "",
                RunOutcome.failed("Contract cannot be read"));
        Mockito.when(executionSummaryProvider.snapshot()).thenReturn(failed);
        ReflectionTestUtils.setField(testCaseListener, "reportingInitialized", false);

        ExecutionSummary result = testCaseListener.endSession();

        Assertions.assertThat(result).isSameAs(failed);
        Mockito.verifyNoInteractions(testReportsGenerator);
        Mockito.verify(executionStatisticsListener, Mockito.never()).markFailed(Mockito.anyString());
    }

    @Test
    void givenATestCase_whenExecutingItAndAWarnHappens_thenTheWarnIsCorrectlyReportedWithinTheTestCase() {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportWarn(logger, "Warn {} happened", "1");
        }, FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResult()).isEqualTo(Level.WARN.toString().toLowerCase(Locale.ROOT));
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Warn 1 happened");
    }

    @Test
    void shouldMineTheErrorSignatureWithTheFieldRolesTheContractDeclares() {
        String contract = """
                openapi: 3.0.3
                info: {title: orders, version: "1"}
                paths:
                  /orders:
                    post:
                      responses:
                        default:
                          description: failure
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  what: {type: string}
                                  trackingRef: {type: string, format: uuid}
                """;
        Object previousSpec = ReflectionTestUtils.getField(catsGlobalContext, "openAPI");
        ReflectionTestUtils.setField(catsGlobalContext, "openAPI", new io.swagger.v3.parser.OpenAPIV3Parser().readContents(contract, null, null).getOpenAPI());
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        try {
            testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
                testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
                testCaseListener.addResponse(CatsResponse.builder().responseCode(400).body("{\"what\":\"Name too long\",\"trackingRef\":\"TR-A7F3\"}").build());
                testCaseListener.addContractPath("/orders");
                testCaseListener.reportError(logger, "Something happened: {}", "bad stuff!");
            }, FuzzingData.builder().build());
        } finally {
            ReflectionTestUtils.setField(catsGlobalContext, "openAPI", previousSpec);
            MDC.remove(TestCaseListener.ID);
        }

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getErrorSignature()).get().satisfies(signature -> Assertions.assertThat(signature.tokens()).containsExactly("Name", "too", "long"));
    }

    @ParameterizedTest
    @CsvSource({"400,true", "500,false"})
    void shouldCaptureRootStringInputsWhileKeepingFullWfcFaultContexts(int status, boolean sharedSignature) {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        String body = "{\"padding\":\"%s\",\"message\":\"Cannot deserialize from String value ('TOKEN_ALPHA')\"}".formatted("x".repeat(1100));
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").payload("\"TOKEN_ALPHA\"").build());
            testCaseListener.addResponse(CatsResponse.builder().responseCode(status).body(body).build());
            testCaseListener.addContractPath("/items");
            testCaseListener.reportError(logger, "Rejected request");
        }, FuzzingData.builder().build());

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getResponseBody()).doesNotContain("TOKEN_ALPHA");
        Assertions.assertThat(summary.getErrorSignature()).get().satisfies(signature -> Assertions.assertThat(signature.tokens()).contains("('<INPUT>')"));
        Assertions.assertThat(summary.getWfcErrorSignature().equals(summary.getErrorSignature())).isEqualTo(sharedSignature);
        if (!sharedSignature) {
            Assertions.assertThat(summary.getWfcErrorSignature()).get().satisfies(signature -> Assertions.assertThat(signature.tokens()).contains("('TOKEN_ALPHA')"));
        }
    }

    @ParameterizedTest
    @CsvSource({"401,1", "403,1", "200,0"})
    void shouldIncreaseTheNumberOfAuthErrors(int respCode, int times) {
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(respCode).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportError(logger, "Something happened: {}", "bad stuff!"));
        Mockito.verify(executionStatisticsListener, Mockito.times(times)).increaseAuthErrors();
    }

    @ParameterizedTest
    @CsvSource({"952,1", "953,1", "954,1", "955,1", "956,1", "957,1", "958,1", "959,0", "999,0", "500,0", "401,0"})
    void shouldIncreaseTheNumberOfIoErrorsForExceptionalResponses(int respCode, int times) {
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(respCode).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportError(logger, "Something happened: {}", "bad stuff!"));
        Mockito.verify(executionStatisticsListener, Mockito.times(times)).increaseIoErrors();
    }

    @Test
    void shouldIncreaseTheNumberOfIOErrors() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            throw new CatsException("something bad", new IOException());
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseIoErrors();
    }

    @Test
    void shouldNotIncreaseIOErrorsForNonIOException() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            throw new CatsException("something bad", new IndexOutOfBoundsException());
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(0)).increaseIoErrors();
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "false,true,false", "true,true,true"})
    void shouldCallInfoInsteadOfWarnWhenIgnoreCodeSupplied(boolean ignoreResponseCodes, boolean ignoreUndocumentedRespCode, boolean ignoreResponseBodyCheck) {
        Mockito.when(ignoreArguments.isIgnoredResponseCode(Mockito.anyString())).thenReturn(ignoreResponseCodes);
        Mockito.when(ignoreArguments.isIgnoreResponseCodeUndocumentedCheck()).thenReturn(ignoreUndocumentedRespCode);
        Mockito.when(ignoreArguments.isIgnoreResponseBodyCheck()).thenReturn(ignoreResponseBodyCheck);

        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("300", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("300", Collections.emptyList()));

        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX));


        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldReportNotMatchingContentType() {
        Mockito.when(ignoreArguments.isIgnoreResponseContentTypeCheck()).thenReturn(false);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).responseContentType("application/json").build();
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getContentTypesByResponseCode("200")).thenReturn(List.of("application/csv"));
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldIgnoreBodyAndContentTypeValidationForHeadResponses() {
        Mockito.when(ignoreArguments.isIgnoreResponseContentTypeCheck()).thenReturn(false);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        CatsResponse response = CatsResponse.builder()
                .body("{\"actual\":true}")
                .jsonBody(JsonParser.parseString("{\"actual\":true}"))
                .responseCode(200)
                .responseContentType("application/json")
                .build();
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.HEAD);
        Mockito.when(data.getPath()).thenReturn("/resources");
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("200", List.of("{\"expected\":true}")));
        Mockito.when(data.getResponseContentTypes()).thenReturn(Map.of("200", List.of("application/csv")));
        Mockito.when(data.getContentTypesByResponseCode("200")).thenReturn(List.of("application/csv"));

        prepareTestCaseListenerSimpleSetup(response,
                () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX));

        Mockito.verify(executionStatisticsListener).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
    }

    @Test
    void shouldReturnUnexpectedButDocumentedResponseCode() {
        Mockito.when(ignoreArguments.isIgnoreResponseContentTypeCheck()).thenReturn(true);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200"));

        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX));

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipInsteadOfInfoWhenSkipReportingIsEnabledAndIgnoredResponseCode() {
        Mockito.when(ignoreArguments.isIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(true);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportInfo(logger, "Something was good"));

        Mockito.verify(executionStatisticsListener).increaseSkippedFromReporting(Mockito.nullable(String.class));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipInfoWhenSkipSuccessIsEnabled() {
        Mockito.when(ignoreArguments.isSkipReportingForSuccess()).thenReturn(true);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportInfo(logger, "Something was good"));

        Mockito.verify(executionStatisticsListener).increaseSkippedFromReporting(Mockito.nullable(String.class));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipWarnWhenSkipWarningsIsEnabled() {
        Mockito.when(ignoreArguments.isSkipReportingForWarnings()).thenReturn(true);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportWarn(logger, "Something was good"));

        Mockito.verify(executionStatisticsListener).increaseSkippedFromReporting(Mockito.nullable(String.class));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());

        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldCallInfoInsteadOfErrorWhenIgnoreCodeSupplied() {
        Mockito.when(ignoreArguments.isIgnoredResponseCode("200")).thenReturn(true);
        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(200).build(), () -> testCaseListener.reportError(logger, "Warn"));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @ParameterizedTest
    @CsvSource({"true,Unexpected response code: 400", "false,Error details leak"})
    void shouldSetResultReasonBasedOnErrorLeaksWhenReportingResultError(boolean notIgnoredResponse, String expectedReason) {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(notIgnoredResponse);
        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(400).body("nullpointerexception").build(), () -> {
            ReflectionTestUtils.invokeMethod(testCaseListener, "extractErrorLeaks");
            testCaseListener.reportResultError(logger, FuzzingData.builder().build(), "Unexpected response code: 400", "Error");
        });

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getError()).isTrue();
        Assertions.assertThat(summary.getResultReason()).isEqualTo(expectedReason);
        MDC.remove(TestCaseListener.ID);
    }

    @ParameterizedTest
    @CsvSource({"200, CONFIRMED, error", "200, FIRM, error", "200, TENTATIVE, warn", "500, TENTATIVE, error"})
    void shouldReportSecurityFindingBasedOnConfidence(int responseCode, FindingConfidence confidence, String expectedResult) {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        CatsResponse response = CatsResponse.builder().responseCode(responseCode).body("{}").build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportSecurityFinding(logger,
                FuzzingData.builder().build(), response, new SecurityFinding("SQL injection vulnerability detected", "Evidence.", confidence)));

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getResult()).isEqualTo(expectedResult);
        Assertions.assertThat(summary.getConfidence()).isEqualTo(confidence);
        Assertions.assertThat(summary.getResultReason()).isEqualTo("SQL injection vulnerability detected");
        Assertions.assertThat(summary.getResultDetails()).isEqualTo("Evidence. Confidence: %s (%s).".formatted(confidence, confidence.description()));
        MDC.remove(TestCaseListener.ID);
    }

    @ParameterizedTest
    @CsvSource({"TENTATIVE, Error details leak, ", "FIRM, XSS payload reflected in response, FIRM"})
    void shouldPreferErrorLeakOverTentativeFindingOnServerError(FindingConfidence confidence, String expectedReason,
                                                                FindingConfidence expectedConfidence) {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        CatsResponse response = CatsResponse.builder().responseCode(500).body("java.lang.NullPointerException at Foo").build();
        prepareTestCaseListenerSimpleSetup(response, () -> {
            ReflectionTestUtils.invokeMethod(testCaseListener, "extractErrorLeaks");
            testCaseListener.reportSecurityFinding(logger, FuzzingData.builder().build(), response,
                    new SecurityFinding("XSS payload reflected in response", "Reflected.", confidence));
        });

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getResult()).isEqualTo("error");
        Assertions.assertThat(summary.getResultReason()).isEqualTo(expectedReason);
        Assertions.assertThat(summary.getConfidence()).isEqualTo(expectedConfidence);
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldPreserveRecordedExchangeAndErrorLeaksWhenCallingOutsideOfTheTestCase() {
        CatsResponse original = CatsResponse.builder().responseCode(200).body("original").build();
        prepareTestCaseListenerSimpleSetup(original, () -> {
            CatsResponse replayed = testCaseListener.callPreservingCurrentExchange(() -> {
                testCaseListener.addRequest(CatsRequest.builder().httpMethod("PUT").build());
                CatsResponse replayResponse = CatsResponse.builder().responseCode(500).body("nullpointerexception").build();
                testCaseListener.addResponse(replayResponse);
                return replayResponse;
            });
            Assertions.assertThat(replayed.getResponseCode()).isEqualTo(500);
            testCaseListener.reportResultError(logger, FuzzingData.builder().build(), "Reason", "Error");
        });

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getHttpResponseCode()).isEqualTo(200);
        Assertions.assertThat(summary.getHttpMethod()).isEqualTo("method");
        Assertions.assertThat(summary.getResult()).isEqualTo("success");
        Assertions.assertThat(summary.getResultReason()).isEqualTo("Reason");
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipTestWhenErrorAndIgnoredCodeSuppliedAndSkipReportingEnabled() {
        Mockito.when(ignoreArguments.isIgnoredResponseCode("200")).thenReturn(true);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(true);

        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(200).build(), () -> testCaseListener.reportError(logger, "Error"));

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener).increaseSkippedFromReporting(Mockito.nullable(String.class));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipTestWhenWarnAndIgnoredCodeSuppliedAndSkipReportingEnabled() {
        Mockito.when(ignoreArguments.isIgnoredResponseCode("200")).thenReturn(true);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(true);

        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(200).build(), () -> testCaseListener.reportWarn(logger, "Warn"));

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener).increaseSkippedFromReporting(Mockito.nullable(String.class));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }


    @Test
    void givenATestCase_whenExecutingItAndAnErrorHappens_thenTheErrorIsCorrectlyReportedWithinTheTestCase() {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportError(logger, "Error {} happened", "1"), FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResult()).isEqualTo(Level.ERROR.toString().toLowerCase(Locale.ROOT));
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Error 1 happened");
    }

    @Test
    void givenATestCase_whenExecutingItAndASuccessHappens_thenTheSuccessIsCorrectlyReportedWithinTheTestCase() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportInfo(logger, "Success {} happened", "1");
        }, FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResult()).isEqualTo("success");
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Success 1 happened");
    }

    @Test
    void givenATestCase_whenSkippingIt_thenTheTestCaseIsNotReported() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.skipTest(logger, "Skipper!"), FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener, Mockito.never())
                .increaseSkippedFromReporting(Mockito.nullable(String.class));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails).isEmpty();
    }

    @Test
    void givenADocumentedResponseThatMatchesTheResponseCodeAndSchema_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("")));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
    }

    @Test
    void shouldReportTruncatedExpectedResponseWithoutSchemaMismatch() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("partial");
        Mockito.when(response.isBodyTruncated()).thenReturn(true);
        Mockito.when(response.getCapturedBodyBytes()).thenReturn(10L);
        Mockito.when(response.getResponseBodyLimit()).thenReturn(10L);
        Mockito.when(response.getDeclaredContentLength()).thenReturn(100L);
        Mockito.when(response.responseCodeAsString()).thenReturn("200");
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200"));
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").build());
            testCaseListener.addResponse(response);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResultReason()).isEqualTo("Response body truncated");
        Assertions.assertThat(testCase.getResultDetails()).contains("captured 10 bytes", "max 10", "Content-Length 100")
                .doesNotContain("schema");
    }

    @Test
    void shouldPreserveUnexpectedStatusErrorForTruncatedResponse() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("partial");
        Mockito.when(response.isBodyTruncated()).thenReturn(true);
        Mockito.when(response.getResponseCode()).thenReturn(500);
        Mockito.when(response.responseCodeAsString()).thenReturn("500");
        Mockito.when(response.getResponseContentType()).thenReturn("text/plain");
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("500"));
        Mockito.when(data.getResponseContentTypes()).thenReturn(Map.of("500", List.of("application/json")));
        Mockito.when(data.getContentTypesByResponseCode("500")).thenReturn(List.of("application/json"));
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").build());
            testCaseListener.addResponse(response);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener).increaseErrors(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResultReason()).isEqualTo("Unexpected response code: 500");
        Assertions.assertThat(testCase.getResultDetails()).contains("500").doesNotContain("content type", "truncated");
    }

    @Test
    void shouldReportDedicatedCallTimeoutResult() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getResponseCode()).thenReturn(959);
        Mockito.when(response.responseCodeAsString()).thenReturn("959");
        Mockito.when(response.getResponseTimeInMs()).thenReturn(1000L);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").build());
            testCaseListener.addResponse(response);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener).increaseErrors(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResultReason()).isEqualTo("Call timeout exceeded");
        Assertions.assertThat(testCase.getResultDetails()).contains("--callTimeout", "1000 ms");
    }

    @Test
    void givenADocumentedResponseThatMatchesTheResponseCodeAndButNotSchema_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("{'test':1}"));
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("nomatch")));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResultDetails()).startsWith("Response does NOT match expected result. Response code");
    }

    @Test
    void shouldRejectResponseMissingRequiredProperty() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("id", new IntegerSchema());
        schema.setRequired(List.of("id"));

        assertSchemaMismatch(schema, "{\"id\":1}", "{}");
    }

    @Test
    void shouldRejectResponsePropertyWithWrongType() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("id", new IntegerSchema());
        schema.setRequired(List.of("id"));

        CatsTestCaseSummary result = assertSchemaMismatch(schema, "{\"id\":1}", "{\"id\":\"wrong\"}");

        Assertions.assertThat(result.getResultDetails()).contains("Validation errors", "id", "integer");
    }

    @Test
    void shouldRejectInvalidArrayElementAfterTheFirst() {
        ObjectSchema itemSchema = new ObjectSchema();
        itemSchema.addProperty("id", new IntegerSchema());
        itemSchema.setRequired(List.of("id"));
        ArraySchema schema = new ArraySchema();
        schema.setItems(itemSchema);

        assertSchemaMismatch(schema, "[{\"id\":1}]", "[{\"id\":1},{\"id\":\"wrong\"}]");
    }

    @Test
    void shouldRejectAdditionalPropertyWhenForbidden() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("id", new IntegerSchema());
        schema.setAdditionalProperties(false);

        assertSchemaMismatch(schema, "{\"id\":1}", "{\"id\":1,\"unexpected\":true}");
    }

    @Test
    void shouldAcceptResponseMatchingSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("id", new IntegerSchema());
        schema.setRequired(List.of("id"));

        executeSchemaValidation(schema, "{\"id\":1}", "{\"id\":42}");

        Mockito.verify(executionStatisticsListener).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
    }

    @Test
    void shouldResolveComponentSchemaReferences() {
        ObjectSchema componentSchema = new ObjectSchema();
        componentSchema.addProperty("id", new IntegerSchema());
        componentSchema.setRequired(List.of("id"));
        Components components = new Components();
        components.setSchemas(Map.of("Resource", componentSchema));
        Schema<?> responseSchema = new Schema<>().$ref("#/components/schemas/Resource");

        assertSchemaMismatch(responseSchema, "{\"id\":1}", "{\"id\":\"wrong\"}", SpecVersion.V30, components);
    }

    @Test
    void shouldValidateOpenApi31Schemas() {
        JsonSchema schema = new JsonSchema();
        schema.setTypes(Set.of("integer", "null"));

        assertSchemaMismatch(schema, "1", "\"wrong\"", SpecVersion.V31, new Components());
    }

    @Test
    void shouldAcceptIssue211ProblemDetailsWithDottedErrorPath() throws IOException {
        Schema<?> problemDetails = Json31.mapper().readValue("""
                {
                  "type": "object",
                  "properties": {
                    "type": {"type": ["null", "string"]},
                    "title": {"type": ["null", "string"]},
                    "status": {"pattern": "^-?(?:0|[1-9]\\\\d*)$", "type": "integer", "format": "int32"},
                    "detail": {"type": ["null", "string"]},
                    "errors": {"type": ["null", "object"], "additionalProperties": {"type": "array", "items": {"type": "string"}}},
                    "traceId": {"type": ["null", "string"]},
                    "code": {"type": ["null", "string"]},
                    "isExpected": {"type": ["null", "boolean"]},
                    "isExceptional": {"type": ["null", "boolean"]}
                  }
                }
                """, Schema.class);
        Schema<?> responseSchema = new Schema<>().$ref("#/components/schemas/ProblemDetails");
        OpenAPI openAPI = new OpenAPI();
        openAPI.setSpecVersion(SpecVersion.V31);
        openAPI.setComponents(new Components().addSchemas("ProblemDetails", problemDetails));
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST).path("/test").openApi(openAPI)
                .schemaMap(openAPI.getComponents().getSchemas())
                .responseCodes(Set.of("400"))
                .responseSchemaDefinitions(Map.of("400", Map.of("application/json", responseSchema)))
                .responseContentTypes(Map.of("400", List.of("application/json")))
                .build();
        String body = """
                {
                  "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
                  "title": "The provided data is invalid in the context of the requested entity or operation",
                  "status": 400,
                  "detail": "The creationCommandDto field is required.",
                  "errors": {
                    "creationCommandDto": ["The creationCommandDto field is required."],
                    "$.networkConfiguration.wan.enabled": ["The input was not valid."]
                  },
                  "traceId": "00-d892df3c78286c216b4311119a5ca83c-b863e808a034fe5c-01",
                  "code": "000010000",
                  "isExpected": true,
                  "isExceptional": false
                }
                """;
        CatsResponse response = CatsResponse.builder()
                .body(body).jsonBody(JsonParser.parseString(body)).responseCode(400)
                .responseContentType("application/json; charset=utf-8")
                .responseValidationField("networkConfiguration#wan#enabled")
                .build();
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(ignoreArguments.isCheckFieldInResponse()).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
            testCaseListener.addResponse(response);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, data);

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails.getFirst().getResultDetails())
                .startsWith("Response matches expected result");
        Mockito.verify(executionStatisticsListener).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());

        CatsResponse missingFieldResponse = CatsResponse.builder()
                .body(body).jsonBody(JsonParser.parseString(body)).responseCode(400)
                .responseContentType("application/json; charset=utf-8")
                .responseValidationField("unrelatedMissingField")
                .build();
        Mockito.when(ignoreArguments.isCheckFieldInResponse()).thenReturn(false);
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
            testCaseListener.addResponse(missingFieldResponse);
            testCaseListener.reportResult(logger, data, missingFieldResponse, ResponseCodeFamilyPredefined.FOURXX);
        }, data);
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());

        Mockito.when(ignoreArguments.isCheckFieldInResponse()).thenReturn(true);
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
            testCaseListener.addResponse(missingFieldResponse);
            testCaseListener.reportResult(logger, data, missingFieldResponse, ResponseCodeFamilyPredefined.FOURXX);
        }, data);
        Mockito.verify(executionStatisticsListener).increaseWarns(Mockito.any());
    }

    @Test
    void shouldRejectInvalidResponseFormats() {
        StringSchema schema = new StringSchema();
        schema.setFormat("date");

        assertSchemaMismatch(schema, "\"2026-09-16\"", "\"not-a-date\"");
    }

    @Test
    void givenAnUndocumentedResponseThatMatchesTheResponseCode_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("400"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("test")));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResultDetails()).startsWith("Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER");
    }

    @Test
    void givenADocumentedResponseThatIsNotExpected_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("400"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("test")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX), FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
    }

    @Test
    void givenAnUndocumentedResponseThatIsNotExpected_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("test")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("4XX");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX), FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.getFirst();
        Assertions.assertThat(testCase.getResultDetails()).startsWith("Unexpected behaviour");
    }

    @ParameterizedTest
    @CsvSource({",", "test", "anEnum"})
    void shouldReportInfoWhenResponseCode400IsExpectedAndResponseBodyMatchesAndFuzzedFieldNullOrPresent(String fuzzedField) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        StringSchema enumSchema = new StringSchema();
        List<String> enumList = new ArrayList<>();
        enumList.add(null);
        enumList.add("value");
        enumSchema.setEnum(enumList);
        Mockito.when(response.getBody()).thenReturn("{'test':1,'anEnum':null}");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("{'test':1,'anEnum':null}"));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("400", Collections.singletonList("{'test':'4','anEnum':'value'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("anEnum", enumSchema));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.getResponseValidationField()).thenReturn(fuzzedField);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [400] is documented and response body matches the corresponding schema.");
    }

    @Test
    void shouldMatchResponseSchemaInsteadOfPartialResponseExample() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        String responseBody = "{\"a\":\"x\",\"b\":\"y\"}";
        Mockito.when(response.getBody()).thenReturn(responseBody);
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString(responseBody));
        ObjectSchema responseSchema = new ObjectSchema();
        responseSchema.addProperty("a", new StringSchema());
        responseSchema.addProperty("b", new StringSchema());
        OpenAPI openAPI = new OpenAPI();
        openAPI.setSpecVersion(SpecVersion.V30);
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("200", List.of("{\"a\":\"x\"}")));
        Mockito.when(data.getOpenApi()).thenReturn(openAPI);
        Mockito.when(data.getResponseSchema("200", "2XX", "application/json")).thenReturn(Optional.of(responseSchema));
        Mockito.when(data.getResponseContentTypes()).thenReturn(Map.of("200", List.of("application/json")));
        Mockito.when(data.getContentTypesByResponseCode("200")).thenReturn(List.of("application/json"));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("2XX");
        Mockito.when(response.getResponseContentType()).thenReturn("application/json");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());

        Mockito.verify(executionStatisticsListener).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener).reportInfo(logger,
                "Response matches expected result. Response code [200] is documented and response body matches the corresponding schema.");
    }

    @ParameterizedTest
    @CsvSource({"{}", "[]", "''", "' '"})
    void shouldReportInfoWhenResponseCode200IsExpectedAndResponseBodyIsEmpty(String body) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn(body);
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString(body));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Collections.emptyMap());
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("4XX");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [400] is documented and response body matches the corresponding schema.");
    }

    @Test
    void shouldReportInfoWhenResponseCode200IsExpectedAndResponseBodyIsArray() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("[{'test':1},{'test':2}]");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("[{'test':1},{'test':2}]"));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [400] is documented and response body matches the corresponding schema.");
    }

    @ParameterizedTest
    @CsvSource(value = {"[]|[{'test':'4'},{'test':'4'}]", "[{'test':1},{'test':2}]|{'test':'4'}"}, delimiter = '|')
    void shouldReportInfoWhenResponseCodeIsExpectedAndResponseBodyAndDocumentedResponsesAreArrays(String returnedBody, String documentedResponses) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn(returnedBody);
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString(returnedBody));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("400", Collections.singletonList(documentedResponses), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [400] is documented and response body matches the corresponding schema.");
    }

    @Test
    void shouldReportInfoWhenResponseCode200IsExpectedAndResponseBodyIsEmptyArrayButResponseIsNotArray() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("[]");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("[]"));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [400] is documented and response body matches the corresponding schema.");
    }

    @ParameterizedTest
    @CsvSource({"application/csv", "application/pdf"})
    void shouldReportInfoWhenResponseCode200AndResponseContentTypeIsAFile(String contentType) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("column1,column2,column3");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("{'notAJson': 'column1,column2,column3'}"));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(data.getContentTypesByResponseCode(Mockito.any())).thenReturn(List.of(contentType));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");
        Mockito.when(response.getResponseContentType()).thenReturn(contentType);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [200] is documented and response body matches the corresponding schema.");
    }

    @Test
    void shouldReportWarnWhenResponseCode400IsExpectedAndResponseBodyMatchesButFuzzedFieldNotPresent() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("{'test':1}"));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.getResponseValidationField()).thenReturn("someField");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(ignoreArguments.isCheckFieldInResponse()).thenReturn(true);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response does NOT match expected result. Response code [400] is documented, but response body does NOT match the corresponding schema.");
    }

    @ParameterizedTest
    @CsvSource({"406,FOURXX_MT", "415,FOURXX_MT", "400,FOURXX"})
    void shouldReportInfoWhenResponseCodeNotNecessarilyDocumentedIsExpectedAndResponseBodyMatchesButFuzzedFieldNotPresent(String responseCode, ResponseCodeFamilyPredefined family) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(response.getJsonBody()).thenReturn(JsonParser.parseString("{'test':1}"));
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("200", "4xx"));
        Mockito.when(data.getResponses()).thenReturn(new TreeMap<>(Map.of("4xx", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}"))));
        Mockito.when(response.responseCodeAsString()).thenReturn(responseCode);
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("4XX");
        Mockito.when(response.getResponseValidationField()).thenReturn("test");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, family);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [%s] is documented and response body matches the corresponding schema.".formatted(responseCode));
    }

    @Test
    void shouldReportErrorWhenFuzzerSuccessfulButResponseTimeExceedsMax() {
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(ignoreArguments.isSkipReportingForSuccess()).thenReturn(false);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(false);
        Mockito.when(reportingArguments.getMaxResponseTime()).thenReturn(10);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addResponse(CatsResponse.builder().responseTimeInMs(100).build());
            spyListener.reportInfo(logger, "Response code expected", "200");
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportError(logger, "Test case executed successfully, but response time exceeds --maxResponseTimeInMs: actual 100, max 10");
    }

    @Test
    void shouldReportErrorWhenKeywordsDetectedInResponse() {
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(ignoreArguments.isSkipReportingForSuccess()).thenReturn(false);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(false);
        Mockito.when(ignoreArguments.isIgnoreErrorLeaksCheck()).thenReturn(false);


        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addResponse(CatsResponse.builder().body("NullPointerException").responseTimeInMs(100).build());
            spyListener.reportInfo(logger, "Response code expected", "200");
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportError(logger, "The following keywords were detected in the response which might suggest an error details leak: [nullpointerexception]");
    }

    @Test
    void shouldReportWarnWhenResponseCode400IsUndocumentedAndResponseBodyMatches() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(reportingArguments.isPrintExecutionStatistics()).thenReturn(true);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(new TreeSet<>(Set.of("200", "401")));
        Mockito.when(data.getResponses()).thenReturn(Map.of("401", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("4XX");
        Mockito.when(response.isValidErrorCode()).thenReturn(true);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            spyListener.addResponse(response);
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        }, FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected %s, actual [400], documented response codes: [200, 401]".formatted(ResponseCodeFamilyPredefined.FOURXX.allowedResponseCodes().toString()));
    }

    @Test
    void shouldReportNotFound() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("401"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("401", Collections.singletonList("{'test':'4'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("404");
        Mockito.when(response.getResponseCode()).thenReturn(404);
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("4XX");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX), FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected [400, 404, 413, 414, 422, 431], actual [404], documented response codes: [401]");
    }

    @Test
    void shouldReportNotImplemented() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("401"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("401", Collections.singletonList("{'test':'4'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("501");
        Mockito.when(response.getResponseCode()).thenReturn(501);
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("501");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX), FuzzingData.builder().build());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response HTTP code 501: you forgot to implement this functionality!");
    }


    @Test
    void shouldReturnIsDiscriminator() {
        Discriminator discriminator = new Discriminator();
        discriminator.setPropertyName("field");
        catsGlobalContext.getDiscriminators().clear();
        catsGlobalContext.recordDiscriminator("", discriminator, List.of());

        Assertions.assertThat(testCaseListener.isFieldNotADiscriminator("field")).isFalse();
    }

    @Test
    void shouldReturnIsNotDiscriminator() {
        Discriminator discriminator = new Discriminator();
        discriminator.setPropertyName("field");
        catsGlobalContext.getDiscriminators().clear();
        catsGlobalContext.getDiscriminators().add(discriminator);

        Assertions.assertThat(testCaseListener.isFieldNotADiscriminator("additionalField")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"DELETE,200", "DELETE,204"})
    void shouldStoreDeleteResponse(String httpMethod, int code) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setResponse(CatsResponse.builder().responseCode(code).build());
        testCase.setRequest(CatsRequest.builder().httpMethod(httpMethod).url("test").build());

        Assertions.assertThat(catsGlobalContext.getSuccessfulDeletes()).isEmpty();
        testCaseListener.storeSuccessfulDelete(testCase);
        Assertions.assertThat(catsGlobalContext.getSuccessfulDeletes()).contains("test");
        catsGlobalContext.getSuccessfulDeletes().clear();
    }

    @ParameterizedTest
    @CsvSource({"DELETE,400", "GET,204", "GET,400"})
    void shouldNotStoreDeleteResponse(String httpMethod, int code) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setResponse(CatsResponse.builder().responseCode(code).build());
        testCase.setRequest(CatsRequest.builder().httpMethod(httpMethod).url("test").build());

        Assertions.assertThat(catsGlobalContext.getSuccessfulDeletes()).isEmpty();
        testCaseListener.storeSuccessfulDelete(testCase);
        Assertions.assertThat(catsGlobalContext.getSuccessfulDeletes()).isEmpty();
    }

    @Test
    void shouldReturnDefaultResponseCodeFamilyWhenConfigNotFound() {
        catsGlobalContext.getFuzzersConfiguration().put("AnotherDummy.expectedResponseCode", "999");
        ResponseCodeFamily resultCodeFromFile = testCaseListener.getExpectedResponseCodeConfiguredFor("Dummy", "test", "post", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(resultCodeFromFile).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @ParameterizedTest
    @CsvSource({"application/json,application/v1+json,true", "application/v2+json,application/v3+json,false", "application/v3+json,application/json,true",
            "application/vnd+json,application/json,true", "application/json,application/xml,false", "application/json; charset=utf,application/json; charset=iso,true",
            "*/*,application/json,true", "application/application,application/application/json,false"})
    void shouldCheckContentTypesEquivalence(String firstContentType, String secondContentType, boolean expected) {
        boolean result = testCaseListener.areContentTypesEquivalent(firstContentType, secondContentType);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldLoadFuzzerSpecificResponseCode() {
        catsGlobalContext.getFuzzersConfiguration().clear();
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.expectedResponseCode", "201,202");
        ReflectionTestUtils.setField(testCaseListener, "globalContext", catsGlobalContext);
        ResponseCodeFamily family = testCaseListener.getExpectedResponseCodeConfiguredFor("fuzzer", "test", "post", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(family).isInstanceOf(ResponseCodeFamilyDynamic.class);

        ResponseCodeFamilyDynamic familyDynamic = (ResponseCodeFamilyDynamic) family;
        Assertions.assertThat(familyDynamic.allowedResponseCodes()).containsOnly("201", "202");
    }

    @Test
    void shouldLoadPathSpecificResponseCode() {
        catsGlobalContext.getFuzzersConfiguration().clear();
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.expectedResponseCode", "201,202");
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.test.expectedResponseCode", "400");

        ReflectionTestUtils.setField(testCaseListener, "globalContext", catsGlobalContext);
        ResponseCodeFamily family = testCaseListener.getExpectedResponseCodeConfiguredFor("fuzzer", "test", "post", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(family).isInstanceOf(ResponseCodeFamilyDynamic.class);

        ResponseCodeFamilyDynamic familyDynamic = (ResponseCodeFamilyDynamic) family;
        Assertions.assertThat(familyDynamic.allowedResponseCodes()).containsOnly("400");
    }

    @Test
    void shouldLoadMethodSpecificResponseCode() {
        catsGlobalContext.getFuzzersConfiguration().clear();
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.expectedResponseCode", "201,202");
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.test.expectedResponseCode", "400");
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.test.post.expectedResponseCode", "500");

        ReflectionTestUtils.setField(testCaseListener, "globalContext", catsGlobalContext);
        ResponseCodeFamily family = testCaseListener.getExpectedResponseCodeConfiguredFor("fuzzer", "test", "post", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(family).isInstanceOf(ResponseCodeFamilyDynamic.class);

        ResponseCodeFamilyDynamic familyDynamic = (ResponseCodeFamilyDynamic) family;
        Assertions.assertThat(familyDynamic.allowedResponseCodes()).containsOnly("500");
    }

    @Test
    void shouldReturnDefaultResponseCodeWhenNoConfiguration() {
        ReflectionTestUtils.setField(testCaseListener, "globalContext", new CatsGlobalContext());
        ResponseCodeFamily family = testCaseListener.getExpectedResponseCodeConfiguredFor("fuzzer", "test", "post", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(family).isInstanceOf(ResponseCodeFamilyPredefined.class);

        ResponseCodeFamilyPredefined familyPredefined = (ResponseCodeFamilyPredefined) family;
        Assertions.assertThat(familyPredefined).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @Test
    void shouldReturnCurrentTestNumber() {
        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().build(), () -> {
        });
        Assertions.assertThat(testCaseListener.getCurrentTestCaseNumber()).isEqualTo(1);
    }

    @Test
    void shouldGenerateTraceId() {
        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().build(), () -> Assertions.assertThat(testCaseListener.getTestIdentifier()).isNotNull());
    }

    @Test
    void shouldReturnCurrentFuzzer() {
        testCaseListener.beforeFuzz(RandomResourcesFuzzer.class, "test", "post");
        String currentFuzzer = testCaseListener.getCurrentFuzzer();
        Assertions.assertThat(currentFuzzer).isEqualTo("RandomResources");
    }

    @Test
    void shouldStartUnknownProgress() {
        FuzzingData data = FuzzingData.builder().contractPath("/test").method(HttpMethod.POST).path("/test").build();
        Mockito.when(reportingArguments.isSummaryInConsole()).thenReturn(true);
        ReflectionTestUtils.setField(catsGlobalContext, "catsConfiguration",
                new CatsConfiguration("/test", "test", "test", List.of(), 1, 2, 2, 3));
        TestCaseListener testCaseListenerSpy = Mockito.spy(testCaseListener);
        testCaseListenerSpy.updateUnknownProgress(data);
        Mockito.verify(testCaseListenerSpy).notifySummaryObservers("/test");
    }

    @Test
    void shouldRenderEachPathOnceInNoColorMode() {
        Mockito.when(reportingArguments.isSummaryInConsole()).thenReturn(true);
        Mockito.when(reportingArguments.isColor()).thenReturn(false);
        Mockito.when(executionStatisticsListener.resultAsStringPerPath("/a")).thenReturn("E 0, W 0, S 1");
        Mockito.when(executionStatisticsListener.resultAsStringPerPath("/b")).thenReturn("E 0, W 0, S 2");
        ReflectionTestUtils.setField(catsGlobalContext, "catsConfiguration",
                new CatsConfiguration("/a", "test", "test", List.of(), 1, 2, 2, 2));
        ReflectionTestUtils.setField(testCaseListener, "globalContext", catsGlobalContext);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            testCaseListener.startSession();
            testCaseListener.notifySummaryObservers("/a");
            testCaseListener.notifySummaryObservers("/b");
            Mockito.when(executionStatisticsListener.resultAsStringPerPath("/a")).thenReturn("E 1, W 2, S 5");
            Mockito.when(executionStatisticsListener.resultAsStringPerPath("/b")).thenReturn("E 2, W 1, S 6");
            testCaseListener.notifySummaryObservers("/a");
            testCaseListener.notifySummaryObservers("/b");
            ReflectionTestUtils.invokeMethod(testCaseListener, "markAllPathsAsDone");
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString(StandardCharsets.UTF_8);
        Assertions.assertThat(output).contains("E 1, W 2, S 5").contains("E 2, W 1, S 6");
    }

    private CatsTestCaseSummary assertSchemaMismatch(Schema<?> schema, String generatedSample, String responseBody) {
        CatsTestCaseSummary result = executeSchemaValidation(schema, generatedSample, responseBody);

        Mockito.verify(executionStatisticsListener).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        return result;
    }

    private CatsTestCaseSummary assertSchemaMismatch(Schema<?> schema, String generatedSample, String responseBody,
                                                     SpecVersion specVersion, Components components) {
        CatsTestCaseSummary result = executeSchemaValidation(schema, generatedSample, responseBody, specVersion, components);

        Mockito.verify(executionStatisticsListener).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        return result;
    }

    private CatsTestCaseSummary executeSchemaValidation(Schema<?> schema, String generatedSample, String responseBody) {
        return executeSchemaValidation(schema, generatedSample, responseBody, SpecVersion.V30, new Components());
    }

    private CatsTestCaseSummary executeSchemaValidation(Schema<?> schema, String generatedSample, String responseBody,
                                                        SpecVersion specVersion, Components components) {
        MediaType mediaType = new MediaType();
        mediaType.setSchema(schema);
        Content content = new Content();
        content.addMediaType("application/json", mediaType);
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setContent(content);
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", apiResponse);
        Operation operation = new Operation();
        operation.setResponses(responses);
        PathItem pathItem = new PathItem();
        pathItem.setGet(operation);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setSpecVersion(specVersion);
        openAPI.setComponents(components);
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .openApi(openAPI)
                .schemaMap(Optional.ofNullable(components.getSchemas()).orElseGet(Collections::emptyMap))
                .responseCodes(Set.of("200"))
                .responses(Map.of("200", List.of(generatedSample)))
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", schema)))
                .responseContentTypes(Map.of("200", List.of("application/json")))
                .build();
        CatsResponse response = CatsResponse.builder()
                .body(responseBody)
                .jsonBody(JsonParser.parseString(responseBody))
                .responseCode(200)
                .responseContentType("application/json")
                .build();
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("GET").build());
            testCaseListener.addResponse(response);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX, true, false);
        }, data);
        return testCaseListener.testCaseSummaryDetails.getFirst();
    }

    private void prepareTestCaseListenerSimpleSetup(CatsResponse build, Runnable runnable) {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addScenario(logger, "Given a {} field", "string");
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.addResponse(build);
            testCaseListener.addFullRequestPath("fullPath");
            testCaseListener.addPath("path");
            testCaseListener.addExpectedResult(logger, "Should return {}", "2XX");
            runnable.run();
        }, FuzzingData.builder().build());
    }

    @Test
    void shouldContinueExecutionWhenNoFilteringEnabled() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(false);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(false);

        boolean result = testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.FOURXX);

        Assertions.assertThat(result).isTrue();
        Mockito.verify(logger, Mockito.never()).skip(Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({"TWOXX,SUCCESS", "FOURXX,REJECTION", "FOURXX_TWOXX,OTHER"})
    void shouldRecordTheDeclaredHttpOracleForCustomResponseProcessors(ResponseCodeFamilyPredefined family, ExpectedResponse.Outcome outcome) {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
            testCaseListener.addResponse(CatsResponse.builder().responseCode(200).body("").build());
            testCaseListener.shouldContinueExecution(logger, family);
            testCaseListener.reportInfo(logger, "Synthetic custom response processor");
        }, FuzzingData.builder().build());

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getExpectedResponseCodes()).containsExactlyElementsOf(family.allowedResponseCodes());
        Assertions.assertThat(summary.getExpectedOutcome()).isEqualTo(outcome);
    }

    @Test
    void shouldReplaceTheDeclaredExpectationWithTheEffectiveConfiguredCodes() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPath()).thenReturn("/items");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("405"));
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        CatsResponse response = CatsResponse.builder().responseCode(405).body("").httpMethod("POST").build();
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("POST").build());
            testCaseListener.addResponse(response);
            testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.TWOXX);
            catsGlobalContext.getFuzzersConfiguration().setProperty(testCaseListener.getCurrentFuzzer() + ".expectedResponseCode", "405");
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX, false, false);
        }, FuzzingData.builder().build());

        CatsTestCaseSummary summary = testCaseListener.testCaseSummaryDetails.getLast();
        Assertions.assertThat(summary.getExpectedResponseCodes()).containsExactly("405");
        Assertions.assertThat(summary.getExpectedOutcome()).isEqualTo(ExpectedResponse.Outcome.REJECTION);
    }

    @Test
    void shouldContinueExecutionWhenExpectedCodeIsNull() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(true);

        boolean result = testCaseListener.shouldContinueExecution(logger, null);

        Assertions.assertThat(result).isTrue();
        Mockito.verify(logger, Mockito.never()).skip(Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldContinueExecutionWhen4xxFilterEnabledAnd4xxExpected() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(true);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(false);

        boolean result = testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.FOURXX);

        Assertions.assertThat(result).isTrue();
        Mockito.verify(logger, Mockito.never()).skip(Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldSkipExecutionWhen4xxFilterEnabledAnd2xxExpected() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(true);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(false);

        boolean result = testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(result).isFalse();
        Mockito.verify(logger).skip("Skipping test - expected response code {} does not match {} (--{}Only enabled)",
                ResponseCodeFamilyPredefined.TWOXX.allowedResponseCodes(), "4XX", "negative");
    }

    @Test
    void shouldContinueExecutionWhen2xxFilterEnabledAnd2xxExpected() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(false);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(true);

        boolean result = testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(result).isTrue();
        Mockito.verify(logger, Mockito.never()).skip(Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldSkipExecutionWhen2xxFilterEnabledAnd4xxExpected() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(false);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(true);

        boolean result = testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.FOURXX);

        Assertions.assertThat(result).isFalse();
        Mockito.verify(logger).skip("Skipping test - expected response code {} does not match {} (--{}Only enabled)",
                ResponseCodeFamilyPredefined.FOURXX.allowedResponseCodes(), "2XX", "positive");
    }

    @Test
    void shouldContinueExecutionWhen4xxFilterEnabledAndSpecific4xxExpected() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(true);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(false);
        ResponseCodeFamily fourZeroFour = new ResponseCodeFamilyDynamic(List.of("404"));

        boolean result = testCaseListener.shouldContinueExecution(logger, fourZeroFour);

        Assertions.assertThat(result).isTrue();
        Mockito.verify(logger, Mockito.never()).skip(Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldSkipExecutionWhen4xxFilterEnabledAnd5xxExpected() {
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(true);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(false);

        boolean result = testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.FIVEXX);

        Assertions.assertThat(result).isFalse();
        Mockito.verify(logger).skip("Skipping test - expected response code {} does not match {} (--{}Only enabled)",
                ResponseCodeFamilyPredefined.FIVEXX.allowedResponseCodes(), "4XX", "negative");
    }

    @Test
    void shouldEscalateWarningToErrorWhenStrictModeIsEnabled() {
        NamingArguments namingArgs = Mockito.mock(NamingArguments.class);
        Mockito.when(namingArgs.isStrict()).thenReturn(true);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "namingArguments", namingArgs);

        try {
            prepareTestCaseListenerSimpleSetup(CatsResponse.builder().build(), () ->
                    testCaseListener.reportResultWarn(logger, FuzzingData.builder().build(), "Strict warning reason", "Warning message"));

            Mockito.verify(executionStatisticsListener).increaseErrors(Mockito.any());
            Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        } finally {
            ReflectionTestUtils.setField(testCaseListener, "namingArguments", null);
        }
    }

    @Test
    void shouldShowTheRerunMessageForAnIncompatibleRuntimeBaseline(@TempDir Path directory) throws Exception {
        Path baseline = directory.resolve("old.json");
        Files.writeString(baseline, "{\"testCases\":[]}");
        QualityGateArguments gate = new QualityGateArguments();
        ReflectionTestUtils.setField(gate, "baseline", baseline.toFile());
        Mockito.when(executionSummaryProvider.getQualityGateArguments()).thenReturn(gate);
        ReflectionTestUtils.setField(testCaseListener, "logger", logger);
        ReflectionTestUtils.invokeMethod(testCaseListener, "evaluateBaselineIfConfigured", List.of());
        Assertions.assertThat(testCaseListener.isBaselineUsageError()).isTrue();
        Assertions.assertThat(gate.getBaselineRegressionDetected()).isTrue();
        Mockito.verify(logger).fatal(Mockito.eq("Unable to load baseline report from {}: {}"), Mockito.eq(baseline.toFile()),
                Mockito.contains("re-run it with this CATS version"));
    }

    @Test
    void shouldEvaluateAndPrintBaselineComparisonAndMatchAdditionalPropertiesResponse(@TempDir Path tempDir) throws Exception {
        Path baselineFile = tempDir.resolve("cats-summary-report.json");
        Files.writeString(baselineFile, "{\"problemKeyVersion\":1,\"problems\":[],\"testCases\":[]}", StandardCharsets.UTF_8);

        QualityGateArguments qgArgs = new QualityGateArguments();
        ReflectionTestUtils.setField(qgArgs, "baseline", baselineFile.toFile());
        ExecutionSummaryProvider provider = Mockito.mock(ExecutionSummaryProvider.class);
        Mockito.when(provider.getQualityGateArguments()).thenReturn(qgArgs);
        Object origProvider = ReflectionTestUtils.getField(testCaseListener, "executionSummaryProvider");
        ReflectionTestUtils.setField(testCaseListener, "executionSummaryProvider", provider);
        try {
            ReflectionTestUtils.invokeMethod(testCaseListener, "evaluateBaselineIfConfigured", List.of());
            ReflectionTestUtils.invokeMethod(testCaseListener, "printBaselineComparisonIfConfigured");
            Assertions.assertThat(qgArgs.getBaselineRegressionDetected()).isFalse();

            ReflectionTestUtils.setField(qgArgs, "baseline", tempDir.resolve("missing.json").toFile());
            ReflectionTestUtils.invokeMethod(testCaseListener, "evaluateBaselineIfConfigured", List.of());
            Assertions.assertThat(qgArgs.getBaselineRegressionDetected()).isTrue();
        } finally {
            ReflectionTestUtils.setField(testCaseListener, "executionSummaryProvider", origProvider);
        }

        Boolean emptyArrayMatch = ReflectionTestUtils.invokeMethod(testCaseListener, "matchesArrayElement", "[]", new JsonArray());
        Assertions.assertThat(emptyArrayMatch).isTrue();
        Boolean emptyArrayMismatch = ReflectionTestUtils.invokeMethod(testCaseListener, "matchesArrayElement", "{}", new JsonArray());
        Assertions.assertThat(emptyArrayMismatch).isFalse();
        Boolean noSchemaMatch = ReflectionTestUtils.invokeMethod(testCaseListener, "matchesSingleElement", "", new JsonObject(), "ROOT");
        Assertions.assertThat(noSchemaMatch).isTrue();

        catsGlobalContext.getAdditionalProperties().put("addPropKey", Map.of());
        Boolean addPropMatch = ReflectionTestUtils.invokeMethod(testCaseListener, "matchesSingleElement", "{\"other\":1}", new JsonObject(), "addPropKey");
        Assertions.assertThat(addPropMatch).isTrue();
        catsGlobalContext.getAdditionalProperties().remove("addPropKey");

        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(404).body("nullpointerexception").build(), () -> {
            testCaseListener.addValidJson(true);
            ReflectionTestUtils.invokeMethod(testCaseListener, "extractErrorLeaks");
            testCaseListener.reportWarn(logger, "Warn with error leak");
        });
    }

    @Test
    void shouldReportRateLimitedResponseAsWarningAndEscalateWhenStrict() {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        CatsResponse rateLimited = CatsResponse.from(429, "{\"error\":\"rate limit\"}", "GET", 15).withRateLimited(3);
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).path("/pets").contractPath("/pets")
                .responseCodes(Set.of("200")).responses(Map.of("200", List.of("{}"))).build();

        prepareTestCaseListenerSimpleSetup(rateLimited, () ->
                testCaseListener.reportResult(logger, data, rateLimited, ResponseCodeFamilyPredefined.TWOXX));

        Mockito.verify(executionStatisticsListener).increaseWarns(Mockito.any());
        Assertions.assertThat(testCaseListener.testCaseSummaryDetails.getLast().getResultReason())
                .isEqualTo("Rate limited");

        NamingArguments namingArgs = Mockito.mock(NamingArguments.class);
        Mockito.when(namingArgs.isStrict()).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "namingArguments", namingArgs);
        try {
            prepareTestCaseListenerSimpleSetup(rateLimited, () ->
                    testCaseListener.reportResult(logger, data, rateLimited, ResponseCodeFamilyPredefined.TWOXX));

            Mockito.verify(executionStatisticsListener).increaseErrors(Mockito.any());
        } finally {
            ReflectionTestUtils.setField(testCaseListener, "namingArguments", null);
        }
    }
}
