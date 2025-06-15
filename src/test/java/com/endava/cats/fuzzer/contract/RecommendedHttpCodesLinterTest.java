package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Collections;

@QuarkusTest
class RecommendedHttpCodesLinterTest {

    private TestCaseListener testCaseListener;

    private RecommendedHttpCodesLinter recommendedHttpCodesContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        recommendedHttpCodesContractInfoFuzzer = new RecommendedHttpCodesLinter(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource(value = {"400,500,200;POST", "400,500,201;POST", "400,500,202;POST", "400,500,204;POST", "400,404,500,201;PUT", "404,500,202;GET", "404,200;HEAD",
            "404,202;HEAD", "404,500,200;DELETE", "404,500,201;DELETE", "404,500,202;DELETE", "404,500,204;DELETE",
            "400,404,500,200;PATCH", "400,404,500,201;PATCH", "400,404,500,202;PATCH", "400,404,500,204;PATCH", "500,200;TRACE"}, delimiter = ';')
    void shouldReportInfoWhenAllResponseCodesAreValid(String responseCode, HttpMethod method) {
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData("PetStore", "lastName", method, responseCode.split(","));

        recommendedHttpCodesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All recommended HTTP codes are defined!"));
    }

    @ParameterizedTest
    @CsvSource(value = {"400,500;POST;200|201|202|204", "400,500;POST;200|201|202|204", "400,202;POST;500", "500,204;POST;400", "404,500,201;PUT;400", "400,500,202;GET;404", "200;HEAD;404",
            "404;HEAD;200|202", "500,201;DELETE;404", "404,202;DELETE;500", "400,404,500;DELETE;200|201|202|204",
            "404,500,200;PATCH;400", "400,500,201;PATCH;404", "400,404,202;PATCH;500", "400,404,500;PATCH;200|201|202|204", "500,400;TRACE;200", "400,200;TRACE;500"}, delimiter = ';')
    void shouldReportErrorWhenAllResponseCodesAreValid(String responseCode, HttpMethod method, String missing) {
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData("PetStore", "lastName", method, responseCode.split(","));

        recommendedHttpCodesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("The following recommended HTTP response codes are missing: {}"), Mockito.eq(Collections.singletonList(missing)));
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(recommendedHttpCodesContractInfoFuzzer).hasToString(recommendedHttpCodesContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(recommendedHttpCodesContractInfoFuzzer.description()).isEqualTo("verifies that the current path contains all recommended HTTP response codes for all operations");
    }
}
