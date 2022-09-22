package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class CatsExecutorTest {

    private CatsExecutor catsExecutor;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    @Inject
    CatsUtil catsUtil;
    private MatchArguments matchArguments;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        matchArguments = Mockito.mock(MatchArguments.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));

        catsExecutor = new CatsExecutor(serviceCaller, testCaseListener, catsUtil, matchArguments);
    }

    @Test
    void shouldSkipWhenFieldFilterNotPassing() {
        catsExecutor.execute(setupContextBuilder().fieldFilter(string -> false).build());

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipWhenSchemaFilterNotPassing() {
        catsExecutor.execute(setupContextBuilder().schemaFilter(string -> false).build());

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldReportResult() {
        catsExecutor.execute(setupContextBuilder().expectedResponseCode(ResponseCodeFamily.FOURXX).build());

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,false", "false,true"})
    void shouldReportError(boolean isMatch, boolean isSupplied) {
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(isMatch);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(isSupplied);
        int times = !isSupplied || isMatch ? 2 : 0;
        catsExecutor.execute(setupContextBuilder().build());

        Mockito.verify(testCaseListener, Mockito.times(times)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldSkip() {
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(false);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        catsExecutor.execute(setupContextBuilder().build());

        Mockito.verify(testCaseListener, Mockito.times(2)).skipTest(Mockito.any(), Mockito.anyString());
    }

    private CatsExecutorContext.CatsExecutorContextBuilder setupContextBuilder() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(data.getPayload()).thenReturn("""
                 {
                    "id": 1,
                    "field": "cats"
                 }
                """);

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.from(
                200, "{}", "POST", 20
        ));

        return CatsExecutorContext.builder()
                .logger(Mockito.mock(PrettyLogger.class))
                .scenario("Replacing value")
                .fuzzValueProducer(schema -> List.of("value1", "value2"))
                .fuzzer(Mockito.mock(Fuzzer.class))
                .fuzzingStrategy(FuzzingStrategy.replace())
                .fuzzingData(data);
    }
}
