package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import io.quarkus.test.junit.QuarkusTest;
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

    @BeforeEach
    void init() {
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
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter("CustomFuzzer");
    }

    @Test
    void shouldRunSecurityFuzzer() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/securityFuzzer.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter("SecurityFuzzer");
    }

    @Test
    void shouldRunSecurityFuzzerFieldTypes() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/securityFuzzer-fieldTypes.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter("SecurityFuzzer");
    }

    @Test
    void shouldThrowExceptionWhenInvalidFile() {
        ReflectionTestUtils.setField(runCommand, "file", new File("src/test/resources/nonExistent.yml"));
        runCommand.run();
        Mockito.verify(filterArguments, Mockito.times(0)).customFilter(Mockito.any());
    }
}
