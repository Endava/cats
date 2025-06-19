package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@QuarkusTest
class ExplainCommandTest {
    @Inject
    ExplainCommand explainCommand;

    @BeforeEach
    void setup() {
        explainCommand = spy(explainCommand);
    }

    @Test
    void testRun_FuzzerType_CallsDisplayFuzzerInfo() {
        explainCommand.type = ExplainCommand.Type.FUZZER;
        explainCommand.info = "TestFuzzer";
        explainCommand.run();
        verify(explainCommand).displayFuzzerInfo();
        verify(explainCommand, never()).displayMutatorInfo();
        verify(explainCommand, never()).displayResponseCodeInfo();
        verify(explainCommand, never()).displayErrorReason();
    }

    @Test
    void testRun_MutatorType_CallsDisplayMutatorInfo() {
        explainCommand.type = ExplainCommand.Type.MUTATOR;
        explainCommand.info = "TestMutator";
        explainCommand.run();
        verify(explainCommand).displayMutatorInfo();
        verify(explainCommand, never()).displayFuzzerInfo();
        verify(explainCommand, never()).displayResponseCodeInfo();
        verify(explainCommand, never()).displayErrorReason();
    }

    @Test
    void testRun_ResponseCodeType_CallsDisplayResponseCodeInfo() {
        explainCommand.type = ExplainCommand.Type.RESPONSE_CODE;
        explainCommand.info = "TestResponseCode";
        explainCommand.run();
        verify(explainCommand).displayResponseCodeInfo();
        verify(explainCommand, never()).displayFuzzerInfo();
        verify(explainCommand, never()).displayMutatorInfo();
        verify(explainCommand, never()).displayErrorReason();
    }

    @Test
    void testRun_ErrorReasonType_CallsDisplayErrorReason() {
        explainCommand.type = ExplainCommand.Type.ERROR_REASON;
        explainCommand.info = "TestErrorReason";
        explainCommand.run();
        verify(explainCommand).displayErrorReason();
        verify(explainCommand, never()).displayFuzzerInfo();
        verify(explainCommand, never()).displayMutatorInfo();
        verify(explainCommand, never()).displayResponseCodeInfo();
    }
}
