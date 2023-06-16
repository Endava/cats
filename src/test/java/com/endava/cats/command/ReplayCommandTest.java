package com.endava.cats.command;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

@QuarkusTest
class ReplayCommandTest {

    @InjectMock
    private ServiceCaller serviceCaller;
    private ReplayCommand replayCommand;

    @InjectSpy
    private TestCaseListener testCaseListener;

    @BeforeEach
    public void setup() {
        replayCommand = new ReplayCommand(serviceCaller, testCaseListener);
        replayCommand.authArgs = Mockito.mock(AuthArguments.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotExecuteIfTestCasesNotSupplied() {
        replayCommand.tests = new String[]{};

        replayCommand.run();
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldExecuteIfTestCasesSupplied(boolean debug) throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        ReflectionTestUtils.setField(replayCommand, "debug", debug);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);
        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldWriteTestsInOutputFolder() throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        ReflectionTestUtils.setField(replayCommand, "outputReportFolder", "replay-report");
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);
        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
        Mockito.verify(testCaseListener).writeHelperFiles();
        Mockito.verify(testCaseListener).writeIndividualTestCase(Mockito.any());
    }

    @Test
    void shouldThrowExceptionWhenTestCasesInvalid() {
        replayCommand.tests = new String[]{"Test1212121212121"};
        replayCommand.run();

        Mockito.verifyNoInteractions(serviceCaller);
    }
}
