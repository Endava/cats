package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.FilterArguments;
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
class VersionsContractInfoFuzzerTest {
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

    private VersionsContractInfoFuzzer versionsContractInfoFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        versionsContractInfoFuzzer = new VersionsContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"/path/{version}/user", "/path/v1/user", "/path/version1.3/user"})
    void shouldReportError(String path) {
        FuzzingData data = FuzzingData.builder().path(path).build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Path contains versioning information"));
    }

    @Test
    void shouldReportInfo() {
        FuzzingData data = FuzzingData.builder().path("/path/user").build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("Path does not contain versioning information"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() {
        FuzzingData data = FuzzingData.builder().path("/path/user").build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("Path does not contain versioning information"));

        Mockito.reset(testCaseListener);
        versionsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportInfo(Mockito.any(), Mockito.eq("Path does not contain versioning information"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(versionsContractInfoFuzzer).hasToString(versionsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(versionsContractInfoFuzzer.description()).isEqualTo("verifies that a given path doesn't contain versioning information");
    }

}
