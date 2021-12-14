package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

@QuarkusTest
class HttpStatusCodeInValidRangeContractInfoFuzzerTest {

    TestCaseListener testCaseListener;

    private HttpStatusCodeInValidRangeContractInfoFuzzer httpStatusCodeInValidRangeContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestCaseExporter.class), Mockito.mock(TestCaseExporter.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        httpStatusCodeInValidRangeContractInfoFuzzer = new HttpStatusCodeInValidRangeContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"100", "200", "599", "default"})
    void shouldReportInfoWhenAllResponseCodesAreValid(String responseCode) {
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData("PetStore", responseCode);

        httpStatusCodeInValidRangeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("All defined response codes are valid!"));
    }

    @ParameterizedTest
    @CsvSource({"99", "1", "600"})
    void shouldReportErrorWhenAllResponseCodesAreValid(String responseCode) {
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData("PetStore", responseCode);

        httpStatusCodeInValidRangeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("The following response codes are not valid: {}"), Mockito.any());
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(httpStatusCodeInValidRangeContractInfoFuzzer).hasToString(httpStatusCodeInValidRangeContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(httpStatusCodeInValidRangeContractInfoFuzzer.description()).isEqualTo("verifies that all HTTP response codes are within the range of 100 to 599");
    }
}
