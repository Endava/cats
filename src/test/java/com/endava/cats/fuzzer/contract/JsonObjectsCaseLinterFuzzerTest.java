package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
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
class JsonObjectsCaseLinterFuzzerTest {

    private TestCaseListener testCaseListener;
    private JsonObjectsCaseLinterFuzzer jsonObjectsCaseLinterFuzzer;

    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(Mockito.mock(TestCaseExporter.class)));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        jsonObjectsCaseLinterFuzzer = new JsonObjectsCaseLinterFuzzer(testCaseListener, Mockito.mock(ProcessingArguments.class), namingArguments);
        ReflectionTestUtils.setField(namingArguments, "jsonPropertiesNaming", NamingArguments.Naming.CAMEL);
        ReflectionTestUtils.setField(namingArguments, "jsonObjectsNaming", NamingArguments.Naming.PASCAL);
    }

    @ParameterizedTest
    @CsvSource({"first_payload,SNAKE", "SecondPayload,PASCAL", "third-payload,KEBAB", "body_120,KEBAB"})
    void shouldMatchRestNamingStandards(String schemaName, NamingArguments.Naming naming) {
        ReflectionTestUtils.setField(namingArguments, "jsonObjectsNaming", naming);
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData(schemaName, "200");

        jsonObjectsCaseLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("JSON elements follow naming conventions."));
    }

    @ParameterizedTest
    @CsvSource({"first_Payload-test", "secondpayload_tesAaa"})
    void shouldReportErrorWhenJsonObjectsNotMatchingCamelCase(String schemaName) {
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData(schemaName, "200");

        jsonObjectsCaseLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("JSON elements do not follow naming conventions: {}"),
                Mockito.contains(schemaName));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(jsonObjectsCaseLinterFuzzer).hasToString(jsonObjectsCaseLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(jsonObjectsCaseLinterFuzzer.description()).isEqualTo("verifies that JSON elements follow naming conventions");
    }
}
