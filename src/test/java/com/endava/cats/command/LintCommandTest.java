package com.endava.cats.command;

import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;

@QuarkusTest
class LintCommandTest {
    @Inject
    ReportingArguments reportingArguments;
    @Inject
    LintCommand lintCommand;
    @Inject
    CatsCommand catsCommand;
    FilterArguments filterArguments;


    @BeforeEach
    void init() {
        filterArguments = Mockito.mock(FilterArguments.class);
        Mockito.when(filterArguments.getCheckArguments()).thenReturn(Mockito.mock(CheckArguments.class));
        ReflectionTestUtils.setField(lintCommand, "contract", "contract");
        ReflectionTestUtils.setField(catsCommand, "filterArguments", filterArguments);
        ReflectionTestUtils.setField(lintCommand, "catsCommand", catsCommand);
    }

    @Test
    void shouldRunContractFuzzers() {
        lintCommand.run();
        Mockito.verify(filterArguments, Mockito.times(1)).customFilter("Contract");
    }
}
