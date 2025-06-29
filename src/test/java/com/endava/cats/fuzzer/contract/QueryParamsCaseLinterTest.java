package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class QueryParamsCaseLinterTest {

    private TestCaseListener testCaseListener;
    private QueryParamsCaseLinter queryParamsCaseLinterFuzzer;

    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        queryParamsCaseLinterFuzzer = new QueryParamsCaseLinter(testCaseListener, namingArguments);
        ReflectionTestUtils.setField(namingArguments, "queryParamsNaming", NamingArguments.Naming.SNAKE);

    }

    @ParameterizedTest
    @CsvSource({"first_payload,SNAKE", "SecondPayload,PASCAL", "third-payload,KEBAB", "X-Rate,HTTP_HEADER"})
    void shouldMatchQueryParamsNamingStandards(String queryParam, NamingArguments.Naming naming) {
        ReflectionTestUtils.setField(namingArguments, "queryParamsNaming", naming);
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData(queryParam, "200");
        data.getQueryParams().add(queryParam);

        queryParamsCaseLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Query params follow naming conventions."));
    }

    @ParameterizedTest
    @CsvSource({"first-payload,SNAKE", "SecondPayload_2,PASCAL", "third_payload,KEBAB", "x_rate,HTTP_HEADER"})
    void shouldNotMatchQueryParamsNamingStandards(String queryParam, NamingArguments.Naming naming) {
        ReflectionTestUtils.setField(namingArguments, "queryParamsNaming", naming);
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData(queryParam, "200");
        data.getQueryParams().add(queryParam);

        queryParamsCaseLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Query params not following recommended naming"), Mockito.eq("Query params not following {} naming: {}"), Mockito.any(), Mockito.contains(queryParam));
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(queryParamsCaseLinterFuzzer).hasToString(queryParamsCaseLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(queryParamsCaseLinterFuzzer.description()).isEqualTo("verifies that query params follow naming conventions");
    }
}
