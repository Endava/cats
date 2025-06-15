package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.List;
import java.util.Set;

@QuarkusTest
class UserDictionaryHeadersFuzzerTest {
    private UserArguments userArguments;
    private MatchArguments matchArguments;
    private UserDictionaryHeadersFuzzer userDictionaryHeadersFuzzer;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ServiceCaller serviceCaller;
    private PrettyLogger prettyLogger;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        userArguments = Mockito.mock(UserArguments.class);
        matchArguments = Mockito.mock(MatchArguments.class);
        prettyLogger = Mockito.mock(PrettyLogger.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        HeadersIteratorExecutor headersIteratorExecutor = new HeadersIteratorExecutor(serviceCaller, testCaseListener, matchArguments, Mockito.mock(FilterArguments.class));
        userDictionaryHeadersFuzzer = new UserDictionaryHeadersFuzzer(headersIteratorExecutor, matchArguments, userArguments);
        ReflectionTestUtils.setField(userDictionaryHeadersFuzzer, "logger", prettyLogger);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(userDictionaryHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(userDictionaryHeadersFuzzer).hasToString(userDictionaryHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldNotRunWhenWordListIsNull() {
        userDictionaryHeadersFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verify(prettyLogger, Mockito.times(1)).error("Skipping fuzzer as --words was not provided!");
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotRunWhenNoMatchArgumentsSupplied() {
        Mockito.when(userArguments.getWords()).thenReturn(new File("test"));
        userDictionaryHeadersFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verify(prettyLogger, Mockito.times(1)).error("Skipping fuzzer as no --m* argument was provided!");
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldReportErrorWhenResponseMatches() {
        Mockito.when(userArguments.getWordsAsList()).thenReturn(List.of("headerFuzz"));
        Mockito.when(userArguments.getWords()).thenReturn(new File("test"));
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(true);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getHeaders()).thenReturn(Set.of(CatsHeader.builder().name("header1").value("value").build()));
        userDictionaryHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldSkipWhenResponseNotMatches() {
        Mockito.when(userArguments.getWordsAsList()).thenReturn(List.of("headerFuzz"));
        Mockito.when(userArguments.getWords()).thenReturn(new File("test"));
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(false);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getHeaders()).thenReturn(Set.of(CatsHeader.builder().name("header1").value("value").build()));
        userDictionaryHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.anyString());
    }
}
