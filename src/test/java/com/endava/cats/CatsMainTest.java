package com.endava.cats;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.factory.FuzzingDataFactory;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig({CatsMain.class, AuthArguments.class, CheckArguments.class, ReportingArguments.class, ApiArguments.class, FilterArguments.class, FilesArguments.class, ProcessingArguments.class})
@SpringBootTest
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
    private ApiArguments apiArguments;

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

        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore.yml");
        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "paths", "contract=src/test/resources/petstore.yml")).isInstanceOf(StopExecutionException.class).hasMessage("list available paths");
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
    }

    @Test
    void givenContractAndServerParameter_whenStartingCats_thenParametersAreProcessedSuccessfully() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(reportingArguments, "logData", "org.apache.wire:debug,com.endava.cats:warn");

        CatsMain spyMain = Mockito.spy(AopTestUtils.<CatsMain>getTargetObject(catsMain));
        spyMain.run("test");
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void givenAnInvalidContractPathAndServerParameter_whenStartingCats_thenAnExceptionIsThrown() {
        ReflectionTestUtils.setField(apiArguments, "contract", "pet.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");

        Assertions.assertThatThrownBy(() -> catsMain.doLogic("list", "fuzzers", "contract=pet.yml")).isInstanceOf(IOException.class).hasMessageContaining("pet.yml");
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void givenAnOpenApiContract_whenStartingCats_thenTheContractIsCorrectlyParsed() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/openapi.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", "true");
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", "true");
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", "true");
        CatsMain spyMain = Mockito.spy(AopTestUtils.<CatsMain>getTargetObject(catsMain));
        spyMain.run("test");
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
        Mockito.verify(fuzzingDataFactory).fromPathItem(Mockito.eq("/pet"), Mockito.any(), Mockito.anyMap(), Mockito.any());
        Mockito.verify(fuzzingDataFactory, Mockito.times(0)).fromPathItem(Mockito.eq("/petss"), Mockito.any(), Mockito.anyMap(), Mockito.any());
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }
}
