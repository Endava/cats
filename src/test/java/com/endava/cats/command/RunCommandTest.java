package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;
import java.io.File;

@QuarkusTest
class RunCommandTest {
    @Inject
    ApiArguments apiArguments;
    @Inject
    RunCommand runCommand;
    @Inject
    CatsCommand catsCommand;
    FilterArguments filterArguments;
    @InjectSpy
    private TestCaseListener testCaseListener;

    @BeforeEach
    void init() {
//        catsCommand = Mockito.mock(CatsCommand.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        ReflectionTestUtils.setField(apiArguments, "contract", "contract");
        ReflectionTestUtils.setField(apiArguments, "server", "server");
        ReflectionTestUtils.setField(catsCommand, "filterArguments", filterArguments);
        ReflectionTestUtils.setField(runCommand, "catsCommand", catsCommand);
    }


    @Test
    void shouldRunCustomFuzzer() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/customFuzzer.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter(Mockito.eq("CustomFuzzer"));
    }

    @Test
    void shouldRunSecurityFuzzer() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/securityFuzzer.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter(Mockito.eq("SecurityFuzzer"));
    }

    @Test
    void shouldThrowExceptionWhenInvalidFile() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/nonExistent.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(0)).customFilter(Mockito.any());
    }
}
