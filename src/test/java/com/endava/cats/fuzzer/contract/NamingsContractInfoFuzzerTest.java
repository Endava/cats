package com.endava.cats.fuzzer.contract;

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
class NamingsContractInfoFuzzerTest {

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private NamingsContractInfoFuzzer namingsContractInfoFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        namingsContractInfoFuzzer = new NamingsContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"/petsPath", "/pets_path", "/pets-path-link", "/pets/Paths", "/pets/complex-Paths", "/pets/{petid10}", "/pets/{pet-id}"})
    void shouldReportError(String path) {
        FuzzingData data = FuzzingData.builder().path(path).build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Path does not follow REST naming good practices: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
    }

    @ParameterizedTest
    @CsvSource({"/pets-paths", "/pets_paths", "/pets-path-links", "/pets/paths", "/pets/complex-paths", "/pets/{petId}", "/pets/{pet_id}"})
    void shouldReportInfo(String path) {
        FuzzingData data = FuzzingData.builder().path(path).build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("Path follows the REST naming good practices."));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(namingsContractInfoFuzzer).hasToString(namingsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(namingsContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI contract elements follow REST API naming good practices");
    }
}
