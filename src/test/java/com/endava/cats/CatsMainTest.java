package com.endava.cats;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.factory.FuzzingDataFactory;
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
@SpringJUnitConfig({CatsMain.class, AuthArguments.class, CheckArguments.class, ReportingArguments.class})
class CatsMainTest {

    @MockBean
    private TestCaseListener testCaseListener;

    @Autowired
    private CatsMain catsMain;

    @Autowired
    private CheckArguments checkArguments;

    @Autowired
    private ReportingArguments reportingArguments;

    @Autowired
    private BuildProperties buildProperties;

    @SpyBean
    private FuzzingDataFactory fuzzingDataFactory;

    @Test
    void givenNoArguments_whenStartingCats_thenHelpIsPrinted() {
        Assertions.assertThatThrownBy(() -> catsMain.doLogic()).isInstanceOf(StopExecutionException.class).hasMessage("list all commands");
    }

    @Test
    void givenAParameter_whenStartingCats_thenParameterIsProcessedSuccessfully() {
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
        ReflectionTestUtils.setField(reportingArguments, "logData", "org.apache.wire:debug");

        CatsMain spyMain = Mockito.spy(catsMain);
        spyMain.run("test");
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
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

    @Test
    void givenAnOpenApiContract_whenStartingCats_thenTheContractIsCorrectlyParsed() {
        ReflectionTestUtils.setField(catsMain, "contract", "src/test/resources/openapi.yml");
        ReflectionTestUtils.setField(catsMain, "server", "http://localhost:8080");
        CatsMain spyMain = Mockito.spy(catsMain);
        spyMain.run("test");
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
        Mockito.verify(fuzzingDataFactory).fromPathItem(Mockito.eq("/pet"), Mockito.any(), Mockito.anyMap(), Mockito.any());
        Mockito.verify(fuzzingDataFactory, Mockito.times(0)).fromPathItem(Mockito.eq("/petss"), Mockito.any(), Mockito.anyMap(), Mockito.any());
        ReflectionTestUtils.setField(catsMain, "contract", "empty");
        ReflectionTestUtils.setField(catsMain, "server", "empty");
    }
}
