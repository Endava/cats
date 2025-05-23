package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@QuarkusTest
class ExplainCommandTest {
    private ExplainCommand explainCommand;

    @BeforeEach
    void setup() {
        explainCommand = spy(new ExplainCommand());
    }

    @Test
    void testRun_FuzzerType_CallsDisplayFuzzerInfo() {
        explainCommand.type = ExplainCommand.Type.FUZZER;
        doNothing().when(explainCommand).displayFuzzerInfo();
        explainCommand.run();
        verify(explainCommand).displayFuzzerInfo();
        verify(explainCommand, never()).displayMutatorInfo();
        verify(explainCommand, never()).displayResponseCodeInfo();
        verify(explainCommand, never()).displayErrorReason();
    }

    @Test
    void testRun_MutatorType_CallsDisplayMutatorInfo() {
        explainCommand.type = ExplainCommand.Type.MUTATOR;
        doNothing().when(explainCommand).displayMutatorInfo();
        explainCommand.run();
        verify(explainCommand).displayMutatorInfo();
        verify(explainCommand, never()).displayFuzzerInfo();
        verify(explainCommand, never()).displayResponseCodeInfo();
        verify(explainCommand, never()).displayErrorReason();
    }

    @Test
    void testRun_ResponseCodeType_CallsDisplayResponseCodeInfo() {
        explainCommand.type = ExplainCommand.Type.RESPONSE_CODE;
        doNothing().when(explainCommand).displayResponseCodeInfo();
        explainCommand.run();
        verify(explainCommand).displayResponseCodeInfo();
        verify(explainCommand, never()).displayFuzzerInfo();
        verify(explainCommand, never()).displayMutatorInfo();
        verify(explainCommand, never()).displayErrorReason();
    }

    @Test
    void testRun_ErrorReasonType_CallsDisplayErrorReason() {
        explainCommand.type = ExplainCommand.Type.ERROR_REASON;
        doNothing().when(explainCommand).displayErrorReason();
        explainCommand.run();
        verify(explainCommand).displayErrorReason();
        verify(explainCommand, never()).displayFuzzerInfo();
        verify(explainCommand, never()).displayMutatorInfo();
        verify(explainCommand, never()).displayResponseCodeInfo();
    }
}
