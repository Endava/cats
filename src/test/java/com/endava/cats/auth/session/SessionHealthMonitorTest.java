package com.endava.cats.auth.session;

import com.endava.cats.auth.wfc.WfcAuthProvider;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class SessionHealthMonitorTest {
    private WfcAuthProvider wfcAuthProvider;
    private SessionHealthMonitor monitor;

    @BeforeEach
    void setup() {
        wfcAuthProvider = Mockito.mock(WfcAuthProvider.class);
        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("user1");
        monitor = new SessionHealthMonitor(wfcAuthProvider);
    }

    @Test
    void shouldIgnoreRequestsWithTamperedCredentialsOrUnknownMethod() {
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, false);
        monitor.recordOutcome("/orders", null, 200, true);

        Mockito.verifyNoInteractions(wfcAuthProvider);
    }

    @Test
    void shouldReportSessionLossWithSuspectMutation() {
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/account/close", HttpMethod.POST, 200, true);

        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any(), Mockito.any(), Mockito.any());
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider).reportSessionLost("user1", "/account/close", "POST");
        Mockito.verify(wfcAuthProvider, Mockito.times(3)).recordSessionHealthy("user1");
    }

    @Test
    void shouldRequireFailuresOnAtLeastTwoKnownGoodOperations() {
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, true);

        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/never-succeeded", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());
    }

    @Test
    void shouldResetFailureStreakOnSuccessButNotOnOtherErrors() {
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 200, true);

        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 403, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 500, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());

        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider).reportSessionLost("user1");
    }

    @Test
    void shouldReportWithoutSuspectWhenLastSuccessWasSafe() {
        monitor.recordOutcome("/account/close", HttpMethod.POST, 200, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 200, true);

        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider).reportSessionLost("user1");
    }

    @Test
    void shouldTrackCredentialsIndependentlyAndUseNeutralWordingWithoutWfc() {
        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("");
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 200, true);
        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("user2");
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());

        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("");
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider).reportSessionLost("");
    }

    @Test
    void shouldForgetObservationsOnReset() {
        monitor.recordOutcome("/orders", HttpMethod.GET, 200, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 200, true);

        monitor.reset();
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/customers", HttpMethod.GET, 401, true);
        monitor.recordOutcome("/orders", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());
    }
}
