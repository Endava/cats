package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.args.FilesArguments;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FieldsIteratorExecutorTest {

    private FieldsIteratorExecutor fieldsIteratorExecutor;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    @Inject
    CatsUtil catsUtil;
    private MatchArguments matchArguments;

    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        matchArguments = Mockito.mock(MatchArguments.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));

        fieldsIteratorExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, catsUtil, matchArguments, filesArguments);
    }

    @Test
    void shouldSkipWhenFieldFilterNotPassing() {
        fieldsIteratorExecutor.execute(setupContextBuilder().fieldFilter(string -> false).build());

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipWhenSchemaFilterNotPassing() {
        fieldsIteratorExecutor.execute(setupContextBuilder().schemaFilter(string -> false).build());

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldReportResult() {
        fieldsIteratorExecutor.execute(setupContextBuilder().expectedResponseCode(ResponseCodeFamily.FOURXX).build());

        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,false", "false,true"})
    void shouldReportError(boolean isMatch, boolean isSupplied) {
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(isMatch);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(isSupplied);
        int times = !isSupplied || isMatch ? 4 : 0;
        fieldsIteratorExecutor.execute(setupContextBuilder().build());

        Mockito.verify(testCaseListener, Mockito.times(times)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldSkip() {
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(false);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        fieldsIteratorExecutor.execute(setupContextBuilder().build());

        Mockito.verify(testCaseListener, Mockito.times(4)).skipTest(Mockito.any(), Mockito.anyString());
    }

    private FieldsIteratorExecutorContext.FieldsIteratorExecutorContextBuilder setupContextBuilder() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(new HashSet<>(Set.of("field", "id")));
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

        return FieldsIteratorExecutorContext.builder()
                .logger(Mockito.mock(PrettyLogger.class))
                .scenario("Replacing value")
                .fuzzValueProducer(schema -> List.of("value1", "value2"))
                .fuzzer(Mockito.mock(Fuzzer.class))
                .fuzzingStrategy(FuzzingStrategy.replace())
                .fuzzingData(data);
    }

    @Test
    void shouldNotRunForFieldsRemovedFromRefData() {
        Mockito.when(filesArguments.getRefData(Mockito.any())).thenReturn(Map.of("id", ServiceCaller.CATS_REMOVE_FIELD));
        fieldsIteratorExecutor.execute(setupContextBuilder().expectedResponseCode(ResponseCodeFamily.FOURXX).build());
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX));
    }
}
