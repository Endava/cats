package com.endava.cats.report;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.report.CatsTestCase;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.event.Level;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Collections;

@ExtendWith(SpringExtension.class)
class TestCaseListenerTest {

    @Mock
    private ExecutionStatisticsListener executionStatisticsListener;

    @Mock
    private TestCaseExporter testCaseExporter;

    @Mock
    private BuildProperties buildProperties;

    @Mock
    private PrettyLogger logger;

    @Mock
    private Fuzzer fuzzer;

    private TestCaseListener testCaseListener;


    @BeforeEach
    void setup() {
        testCaseListener = new TestCaseListener(executionStatisticsListener, testCaseExporter, buildProperties);
    }

    @AfterEach
    void tearDown() {
        CatsMain.TEST.set(0);
    }

    @Test
    void givenAFunction_whenExecutingATestCase_thenTheCorrectContextIsCreatedAndTheTestCaseIsWrittenToFile() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> executionStatisticsListener.increaseSkipped());

        Assertions.assertThat(testCaseListener.testCaseMap.get("Test 1")).isNotNull();
        Mockito.verify(testCaseExporter).writeToFile(Mockito.any());
    }

    @Test
    void givenAFunction_whenExecutingATestCaseAndAddingDetails_thenTheDetailsAreCorrectlyAttachedToTheTestCase() {
        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");

        Assertions.assertThat(testCase).isNull();

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> {
            testCaseListener.addScenario(logger, "Given a {} field", "string");
            testCaseListener.addRequest(new CatsRequest());
            testCaseListener.addResponse(CatsResponse.builder().build());
            testCaseListener.addFullRequestPath("fullPath");
            testCaseListener.addPath("path");
            testCaseListener.addExpectedResult(logger, "Should return {}", "2XX");
        });

        testCase = testCaseListener.testCaseMap.get("Test 1");
        Assertions.assertThat(testCase).isNotNull();
        Assertions.assertThat(testCase.getRequest()).isNotNull();
        Assertions.assertThat(testCase.getResponse()).isNotNull();
        Assertions.assertThat(testCase.getFullRequestPath()).isEqualTo("fullPath");
        Assertions.assertThat(testCase.getPath()).isEqualTo("path");
        Assertions.assertThat(testCase.getScenario()).isEqualTo("Given a string field");
        Assertions.assertThat(testCase.getExpectedResult()).isEqualTo("Should return 2XX");
    }

    @Test
    void givenATestCase_whenExecutingStartAndEndSession_thenTheSummaryAndReportFilesAreCreated() {
        Mockito.when(buildProperties.getName()).thenReturn("CATS");
        Mockito.when(buildProperties.getVersion()).thenReturn("1.1");
        Mockito.when(buildProperties.getTime()).thenReturn(Instant.now());

        testCaseListener.startSession();
        testCaseListener.endSession();

        Mockito.verify(buildProperties, Mockito.times(1)).getName();
        Mockito.verify(buildProperties, Mockito.times(1)).getVersion();
        Mockito.verify(buildProperties, Mockito.times(1)).getTime();
        Mockito.verify(testCaseExporter, Mockito.times(1)).writeReportFiles();
        Mockito.verify(testCaseExporter, Mockito.times(1)).writeSummary(Mockito.anyMap(), Mockito.eq(0), Mockito.eq(0), Mockito.eq(0), Mockito.eq(0));
    }

    @Test
    void givenATestCase_whenExecutingItAndAWarnHappens_thenTheWarnIsCorrectlyReportedWithinTheTestCase() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportWarn(logger, "Warn {} happened", "1"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();

        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
        Assertions.assertThat(testCase.getResult()).isEqualTo(Level.WARN.toString().toLowerCase());
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Warn 1 happened");
    }

    @Test
    void givenATestCase_whenExecutingItAndAnErrorHappens_thenTheErrorIsCorrectlyReportedWithinTheTestCase() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportError(logger, "Error {} happened", "1"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();

        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
        Assertions.assertThat(testCase.getResult()).isEqualTo(Level.ERROR.toString().toLowerCase());
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Error 1 happened");
    }

    @Test
    void givenATestCase_whenExecutingItAndASuccessHappens_thenTheSuccessIsCorrectlyReportedWithinTheTestCase() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportInfo(logger, "Success {} happened", "1"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors();

        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
        Assertions.assertThat(testCase.getResult()).isEqualTo("success");
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Success 1 happened");
    }

    @Test
    void givenATestCase_whenSkippingIt_thenTheTestCaseIsCorrectlySkipped() {
        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.skipTest(logger, "Skipper!"));

        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSkipped();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseWarns();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseErrors();

        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
        Assertions.assertThat(testCase.getResult()).isEqualTo("skipped");
        Assertions.assertThat(testCase.getResultDetails()).isEqualTo("Skipped due to: Skipper!");
    }

    @Test
    void givenADocumentedResponseThatMatchesTheResponseCodeAndSchema_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("")));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
    }

    @Test
    void givenADocumentedResponseThatMatchesTheResponseCodeAndButNotSchema_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("nomatch")));
        Mockito.when(response.responseCodeAsString()).thenReturn("200");

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();
        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
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

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();
        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
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

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();
    }

    @Test
    void givenAnUndocumentedResponseThatIsNotExpected_whenReportingTheResult_thenTheResultIsCorrectlyReported() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));
        Mockito.when(data.getResponses()).thenReturn(Collections.singletonMap("200", Collections.singletonList("test")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        testCaseListener.createAndExecuteTest(logger, fuzzer, () -> testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.TWOXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseErrors();
        Mockito.verify(executionStatisticsListener, Mockito.never()).increaseSuccess();
        CatsTestCase testCase = testCaseListener.testCaseMap.get("Test 1");
        Assertions.assertThat(testCase.getResultDetails()).startsWith("Unexpected behaviour");
    }

    @ParameterizedTest
    @CsvSource({",", "test"})
    void shouldReportInfoWhenResponseCode400IsExpectedAndResponseBodyMatchesAndFuzzedFieldNullOrPresent(String fuzzedField) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.getFuzzedField()).thenReturn(fuzzedField);

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [{}] is documented amd response body matches the corresponding schema.", response.responseCodeAsString());
    }

    @ParameterizedTest
    @CsvSource({"{}", "[]", "''", "' '"})
    void shouldReportInfoWhenResponseCode200IsExpectedAndResponseBodyIsEmpty(String body) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn(body);
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(Collections.emptyMap());
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [{}] is documented amd response body matches the corresponding schema.", response.responseCodeAsString());
    }

    @Test
    void shouldReportInfoWhenResponseCode200IsExpectedAndResponseBodyIsArray() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("[{'test':1},{'test':2}]");
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [{}] is documented amd response body matches the corresponding schema.", response.responseCodeAsString());
    }

    @ParameterizedTest
    @CsvSource(value = {"[]|[{'test':'4'},{'test':'4'}]", "[{'test':1},{'test':2}]|{'test':'4'}"}, delimiter = '|')
    void shouldReportInfoWhenResponseCodeIsExpectedAndResponseBodyAndDocumentedResponsesAreArrays(String returnedBody, String documentedResponses) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn(returnedBody);
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("400", Collections.singletonList(documentedResponses), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [{}] is documented amd response body matches the corresponding schema.", response.responseCodeAsString());
    }

    @Test
    void shouldReportInfoWhenResponseCode200IsExpectedAndResponseBodyIsEmptyArrayButResponseIsNotArray() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("[]");
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns();
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response does NOT match expected result. Response code [{}] is documented, but response body does NOT matches the corresponding schema.", response.responseCodeAsString());
    }

    @Test
    void shouldReportWarnWhenResponseCode400IsExpectedAndResponseBodyMatchesButFuzzedFieldNotPresent() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");
        Mockito.when(response.getFuzzedField()).thenReturn("someField");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns();
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response does NOT match expected result. Response code [{}] is documented, but response body does NOT matches the corresponding schema.", response.responseCodeAsString());
    }

    @ParameterizedTest
    @CsvSource({"406", "415"})
    void shouldReportWarnWhenResponseCodeNotNecessarilyDocumentedIsExpectedAndResponseBodyMatchesButFuzzedFieldNotPresent(String responseCode) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "400"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("400", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn(responseCode);
        Mockito.when(response.getFuzzedField()).thenReturn("someField");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX_MT));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseSuccess();
        Mockito.verify(spyListener, Mockito.times(1)).reportInfo(logger, "Response matches expected result. Response code [{}] is documented amd response body matches the corresponding schema.", response.responseCodeAsString());
    }

    @Test
    void shouldReportWarnWhenResponseCode400IsUndocumentedAndResponseBodyMatches() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        TestCaseListener spyListener = Mockito.spy(testCaseListener);
        Mockito.when(response.getBody()).thenReturn("{'test':1}");
        Mockito.when(data.getResponseCodes()).thenReturn(Sets.newHashSet("200", "401"));
        Mockito.when(data.getResponses()).thenReturn(ImmutableMap.of("401", Collections.singletonList("{'test':'4'}"), "200", Collections.singletonList("{'other':'2'}")));
        Mockito.when(response.responseCodeAsString()).thenReturn("400");

        spyListener.createAndExecuteTest(logger, fuzzer, () -> spyListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX));
        Mockito.verify(executionStatisticsListener, Mockito.times(1)).increaseWarns();
        Mockito.verify(spyListener, Mockito.times(1)).reportWarn(logger, "Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected {}, actual [{}], documented response codes: {}", ResponseCodeFamily.FOURXX.allowedResponseCodes(), response.responseCodeAsString(), data.getResponseCodes());
    }


}
