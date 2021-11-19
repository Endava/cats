package com.endava.cats.command;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;

@ExtendWith(SpringExtension.class)
class ReplayCommandTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private FilterArguments filterArguments;

    private ReplayCommand replayCommand;

    @BeforeEach
    public void setup() {
        replayCommand = new ReplayCommand(serviceCaller, filterArguments);
    }

    @Test
    void shouldNotExecuteIfTestCasesNotSupplied() {
        Mockito.when(filterArguments.areTestCasesSupplied()).thenReturn(Boolean.FALSE);
        replayCommand.execute();
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldExecuteIfTestCasesSupplied() throws Exception {
        Mockito.when(filterArguments.areTestCasesSupplied()).thenReturn(Boolean.TRUE);
        Mockito.when(filterArguments.parseTestCases()).thenReturn(List.of("src/test/resources/Test12.json"));
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(Mockito.mock(CatsResponse.class));
        replayCommand.execute();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
    }

    @Test
    void shouldThrowExceptionWhenTestCasesInvalid() {
        Mockito.when(filterArguments.areTestCasesSupplied()).thenReturn(Boolean.TRUE);
        Mockito.when(filterArguments.parseTestCases()).thenReturn(List.of("Test12"));
        replayCommand.execute();

        Mockito.verifyNoInteractions(serviceCaller);
    }
}
