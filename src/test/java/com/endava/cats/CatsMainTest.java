package com.endava.cats;

import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig({CatsMain.class})
class CatsMainTest {

    @MockBean
    private TestCaseListener testCaseListener;

    @SpyBean
    private CatsMain catsMain;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @Autowired
    private BuildProperties buildProperties;

    @Test
    void givenNoArguments_whenStartingCats_thenHelpIsPrinted() {
        Assertions.assertThatThrownBy(() -> catsMain.doLogic()).isInstanceOf(StopExecutionException.class).hasMessage("minimum parameters not supplied");
    }

    @Test
    void givenAParameter_whenStartingCats_thenParameterIsProcessedSuccessfully() {
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("commands")).isInstanceOf(StopExecutionException.class).hasMessage("list all commands");
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("help")).isInstanceOf(StopExecutionException.class).hasMessage("help");
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("version")).isInstanceOf(StopExecutionException.class).hasMessage("version");
    }

    @Test
    void givenTwoParameters_whenStartingCats_thenParametersAreProcessedSuccessfully() {
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "fuzzers")).isInstanceOf(StopExecutionException.class).hasMessage("list fuzzers");
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "fieldsFuzzerStrategies")).isInstanceOf(StopExecutionException.class).hasMessage("list fieldsFuzzerStrategies");
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "fuzzers")).isInstanceOf(StopExecutionException.class).hasMessage("list fuzzers");

        ReflectionTestUtils.setField(catsMain, "contract", "src/test/resources/petstore.yml");
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "paths", "contract=src/test/resources/petstore.yml")).isInstanceOf(StopExecutionException.class).hasMessage("list available paths");
        ReflectionTestUtils.setField(catsMain, "contract", "empty");
    }

    @Test
    void givenContractAndServerParameter_whenStartingCats_thenParametersAreProcessedSuccessfully() {
        ReflectionTestUtils.setField(catsMain, "contract", "src/test/resources/petstore.yml");
        ReflectionTestUtils.setField(catsMain, "server", "http://localhost:8080");
        catsMain.run();
        Mockito.verify(catsMain).createOpenAPI();
        Mockito.verify(catsMain).startFuzzing(Mockito.any(), Mockito.anyList());
        ReflectionTestUtils.setField(catsMain, "contract", "empty");
        ReflectionTestUtils.setField(catsMain, "server", "empty");

    }


    @Test
    void givenAnInvalidContractPathAndServerParameter_whenStartingCats_thenAnExceptionIsThrown() {
        ReflectionTestUtils.setField(catsMain, "contract", "pet.yml");
        ReflectionTestUtils.setField(catsMain, "server", "http://localhost:8080");

        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "fuzzers", "contract=pet.yml")).isInstanceOf(StopExecutionException.class).hasMessage(null);
        ReflectionTestUtils.setField(catsMain, "contract", "empty");
        ReflectionTestUtils.setField(catsMain, "server", "empty");
    }
}
