package com.endava.cats.report;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.http.RandomResourcesFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.FuzzingData;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

@QuarkusTest
class TestCaseListenerTest {

    TestCaseListener testCaseListener;

    ExecutionStatisticsListener executionStatisticsListener;
    IgnoreArguments ignoreArguments;
    ReportingArguments reportingArguments;
    @Inject
    CatsGlobalContext catsGlobalContext;

    private PrettyLogger logger;
    private Fuzzer fuzzer;
    private TestCaseExporter testCaseExporter;


    @BeforeEach
    void setup() {
        logger = Mockito.mock(PrettyLogger.class);
        fuzzer = Mockito.mock(Fuzzer.class);
        reportingArguments = Mockito.mock(ReportingArguments.class);
        testCaseExporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(reportingArguments.getReportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_JS);
        Mockito.when(testCaseExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_JS);
        executionStatisticsListener = Mockito.mock(ExecutionStatisticsListener.class);
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(testCaseExporter));
        testCaseListener = new TestCaseListener(catsGlobalContext, executionStatisticsListener, exporters, ignoreArguments, reportingArguments);
        catsGlobalContext.getDiscriminators().clear();
        catsGlobalContext.getFuzzersConfiguration().clear();
    }

    @AfterEach
    void tearDown() {
        TestCaseListener.TEST.set(0);
    }

    @Test
    void shouldNotCallInitPathWhenReplayTests() {
        ReflectionTestUtils.setField(testCaseListener, "appName", "CATS");
        testCaseListener.startSession();

        Mockito.verify(testCaseExporter, Mockito.times(1)).reportFormat();
        Mockito.verifyNoMoreInteractions(testCaseExporter);
    }

    @Test
    void givenAFunction_whenExecutingATestCase_thenTheCorrectContextIsCreatedAndTheTestCaseIsWrittenToFile() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
        });

        Assertions.assertThat(testCaseListener.testCaseSummaryDetails.get(0)).isNotNull();
        Mockito.verify(testCaseExporter).writeTestCase(Mockito.any());
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
        });

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
        Assertions.assertThat(testCase).isNotNull();
        Assertions.assertThat(testCase.getPath()).isEqualTo("path");
        Assertions.assertThat(testCase.getScenario()).isEqualTo("Given a string field");
    }

    @Test
    void givenATestCase_whenExecutingStartAndEndSession_thenTheSummaryAndReportFilesAreCreated() {
        ReflectionTestUtils.setField(testCaseListener, "appName", "CATS");
        testCaseListener.startSession();
        testCaseListener.endSession();

        Mockito.verify(testCaseExporter, Mockito.times(1)).writeHelperFiles();
        Mockito.verify(testCaseExporter, Mockito.times(1)).writeSummary(Mockito.anyList(), Mockito.any());
    }

    @Test
    void givenATestCase_whenExecutingItAndAWarnHappens_thenTheWarnIsCorrectlyReportedWithinTheTestCase() {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportWarn(logger, "Warn {} happened", "1");
        });

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
        Assertions.assertThat(testCase.getResult()).isEqualTo(Level.WARN.toString().toLowerCase());
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Warn 1 happened");
    }

    @ParameterizedTest
    @CsvSource({"401,1", "403,1", "200,0"})
    void shouldIncreaseTheNumberOfAuthErrors(int respCode, int times) {
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(respCode).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportError(logger, "Something happened: {}", "bad stuff!"));
        Mockito.verify(executionStatisticsListener, Mockito.times(times)).increaseAuthErrors();
    }

    @Test
    void shouldIncreaseTheNumberOfIOErrors() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            throw new CatsException("something bad", new IOException());
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseIoErrors();
    }

    @Test
    void shouldNotIncreaseIOErrorsForNonIOException() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            throw new CatsException("something bad", new IndexOutOfBoundsException());
        });
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
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
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
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        MDC.remove(TestCaseListener.ID);
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
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipInsteadOfInfoWhenSkipReportingIsEnabledAndIgnoredResponseCode() {
        Mockito.when(ignoreArguments.isIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(true);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportInfo(logger, "Something was good"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipInfoWhenSkipSuccessIsEnabled() {
        Mockito.when(ignoreArguments.isSkipReportingForSuccess()).thenReturn(true);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportInfo(logger, "Something was good"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipWarnWhenSkipWarningsIsEnabled() {
        Mockito.when(ignoreArguments.isSkipReportingForWarnings()).thenReturn(true);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        prepareTestCaseListenerSimpleSetup(response, () -> testCaseListener.reportWarn(logger, "Something was good"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());

        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldStorePostRequestAndRemoveAfterDelete() {
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getResponseCodes()).thenReturn(Set.of("300", "400"));
        Mockito.when(data.getResponses()).thenReturn(Map.of("300", Collections.emptyList()));
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/test");
        MDC.put(TestCaseListener.ID, "1");
        testCaseListener.testCaseMap.put("1", new CatsTestCase());
        testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());

        testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(catsGlobalContext.getPostSuccessfulResponses()).hasSize(1).containsKey("/test");
        Assertions.assertThat(catsGlobalContext.getPostSuccessfulResponses().get("/test")).isNotEmpty();

        Mockito.when(data.getMethod()).thenReturn(HttpMethod.DELETE);
        Mockito.when(data.getPath()).thenReturn("/test/{testId}");
        testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(catsGlobalContext.getPostSuccessfulResponses()).hasSize(1).containsKey("/test");
        Assertions.assertThat(catsGlobalContext.getPostSuccessfulResponses().get("/test")).isEmpty();

        MDC.remove(TestCaseListener.ID);
        testCaseListener.testCaseMap.clear();
    }

    @Test
    void shouldCallInfoInsteadOfErrorWhenIgnoreCodeSupplied() {
        Mockito.when(ignoreArguments.isIgnoredResponseCode("200")).thenReturn(true);
        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(200).build(), () -> testCaseListener.reportError(logger, "Warn"));
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }

    @Test
    void shouldSkipTestWhenErrorAndIgnoredCodeSuppliedAndSkipReportingEnabled() {
        Mockito.when(ignoreArguments.isIgnoredResponseCode("200")).thenReturn(true);
        Mockito.when(ignoreArguments.isSkipReportingForIgnoredCodes()).thenReturn(true);

        prepareTestCaseListenerSimpleSetup(CatsResponse.builder().responseCode(200).build(), () -> testCaseListener.reportError(logger, "Error"));

        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
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
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        MDC.remove(TestCaseListener.ID);
    }


    @Test
    void givenATestCase_whenExecutingItAndAnErrorHappens_thenTheErrorIsCorrectlyReportedWithinTheTestCase() {
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportError(logger, "Error {} happened", "1"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
        Assertions.assertThat(testCase.getResult()).isEqualTo(Level.ERROR.toString().toLowerCase());
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Error 1 happened");
    }

    @Test
    void givenATestCase_whenExecutingItAndASuccessHappens_thenTheSuccessIsCorrectlyReportedWithinTheTestCase() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            testCaseListener.reportInfo(logger, "Success {} happened", "1");
        });

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors(Mockito.any());

        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
        Assertions.assertThat(testCase.getResult()).isEqualTo("success");
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Success 1 happened");
    }

    @Test
    void givenATestCase_whenSkippingIt_thenTheTestCaseIsNotReported() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.skipTest(logger, "Skipper!"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
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
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
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
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
        Assertions.assertThat(testCase.getResultDetails()).startsWith("Response does NOT match expected result. Response code");
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
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
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

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX));
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

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess(Mockito.any());
        CatsTestCaseSummary testCase = testCaseListener.testCaseSummaryDetails.get(0);
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
        Mockito.when(response.getFuzzedField()).thenReturn(fuzzedField);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [400] is documented and response body matches the corresponding schema.");
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
        });
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
        });
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
        });
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
        });
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
        });
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
        Mockito.when(response.getFuzzedField()).thenReturn("someField");
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX);
        });
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
        Mockito.when(response.getFuzzedField()).thenReturn("test");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addRequest(CatsRequest.builder().httpMethod("method").build());
            spyListener.reportResult(logger, data, response, family);
        });
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
        });
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportError(logger, "Test case executed successfully, but response time exceeds --maxResponseTimeInMs: actual 100, max 10");
    }

    @Test
    void shouldReportWarnWhenResponseCode400IsUndocumentedAndResponseBodyMatches() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(ignoreArguments.isNotIgnoredResponse(Mockito.any())).thenReturn(true);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(new TreeSet<>(Set.of("200", "401")));
        Mockito.when(data.getResponses()).thenReturn(Map.of("401", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.responseCodeAsResponseRange()).thenReturn("4XX");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX));
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

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportError(logger, "Response HTTP code 404: you might need to provide business context using --refData or --urlParams");
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

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns(Mockito.any());
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response HTTP code 501: you forgot to implement this functionality!");
    }


    @Test
    void shouldReturnIsDiscriminator() {
        Discriminator discriminator = new Discriminator();
        discriminator.setPropertyName("field");
        catsGlobalContext.getDiscriminators().clear();
        catsGlobalContext.getDiscriminators().add(discriminator);

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
        ResponseCodeFamily resultCodeFromFile = testCaseListener.getExpectedResponseCodeConfiguredFor("Dummy", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(resultCodeFromFile).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @ParameterizedTest
    @CsvSource({"application/json,application/v1+json,true", "application/v2+json,application/v3+json,false", "application/v3+json,application/json,true",
            "application/vnd+json,application/json,true", "application/json,application/xml,false", "application/json; charset=utf,application/json; charset=iso,true",
            "*/*,application/json,true"})
    void shouldCheckContentTypesEquivalence(String firstContentType, String secondContentType, boolean expected) {
        boolean result = TestCaseListener.areContentTypesEquivalent(firstContentType, secondContentType);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldLoadFuzzerSpecificResponseCode() {
        catsGlobalContext = new CatsGlobalContext();
        catsGlobalContext.getFuzzersConfiguration().setProperty("fuzzer.expectedResponseCode", "201,202");
        ReflectionTestUtils.setField(testCaseListener, "globalContext", catsGlobalContext);
        ResponseCodeFamily family = testCaseListener.getExpectedResponseCodeConfiguredFor("fuzzer", ResponseCodeFamilyPredefined.TWOXX);

        Assertions.assertThat(family).isInstanceOf(ResponseCodeFamilyDynamic.class);

        ResponseCodeFamilyDynamic familyDynamic = (ResponseCodeFamilyDynamic) family;
        Assertions.assertThat(familyDynamic.allowedResponseCodes()).containsOnly("201", "202");
    }

    @Test
    void shouldReturnDefaultResponseCodeWhenNoConfiguration() {
        ReflectionTestUtils.setField(testCaseListener, "globalContext", new CatsGlobalContext());
        ResponseCodeFamily family = testCaseListener.getExpectedResponseCodeConfiguredFor("fuzzer", ResponseCodeFamilyPredefined.TWOXX);

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
    void shouldReturnCurrentFuzzer() {
        testCaseListener.beforeFuzz(RandomResourcesFuzzer.class, "test", "post");
        String currentFuzzer = testCaseListener.getCurrentFuzzer();
        Assertions.assertThat(currentFuzzer).isEqualTo("RandomResources");
    }

    @Test
    void shouldStartUnknownProgress() {
        FuzzingData data = FuzzingData.builder().contractPath("/test").method(HttpMethod.POST).path("/test").build();
        Mockito.when(reportingArguments.isSummaryInConsole()).thenReturn(true);
        TestCaseListener testCaseListenerSpy = Mockito.spy(testCaseListener);
        testCaseListenerSpy.updateUnknownProgress(data);
        Mockito.verify(testCaseListenerSpy).notifySummaryObservers(Mockito.eq("/test"));
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
        });
    }
}
