package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

@QuarkusTest
class HttpStatusCodeInRangeLinterTest {

    TestCaseListener testCaseListener;

    private HttpStatusCodeInRangeLinter httpStatusCodeInValidRangeContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        httpStatusCodeInValidRangeContractInfoFuzzer = new HttpStatusCodeInRangeLinter(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"100", "200", "599", "default"})
    void shouldReportInfoWhenAllResponseCodesAreValid(String responseCode) {
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData("PetStore", responseCode);

        httpStatusCodeInValidRangeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All defined response codes are valid!"));
    }

    @ParameterizedTest
    @CsvSource({"99", "1", "600"})
    void shouldReportErrorWhenAllResponseCodesAreValid(String responseCode) {
        FuzzingData data = ContractFuzzerDataUtilForTest.prepareFuzzingData("PetStore", responseCode);

        httpStatusCodeInValidRangeContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("The following response codes are not valid: {}"), Mockito.any());
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
