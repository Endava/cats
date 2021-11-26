package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class HttpStatusCodeInValidRangeContractInfoFuzzerTest {


    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private IgnoreArguments ignoreArguments;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private HttpStatusCodeInValidRangeContractInfoFuzzer httpStatusCodeInValidRangeContractInfoFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
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
