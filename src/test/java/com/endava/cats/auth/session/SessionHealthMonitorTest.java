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
        monitor.record("/orders", HttpMethod.GET, 200, false);
        monitor.record("/orders", null, 200, true);

        Mockito.verifyNoInteractions(wfcAuthProvider);
    }

    @Test
    void shouldReportSessionLossWithSuspectMutation() {
        monitor.record("/orders", HttpMethod.GET, 200, true);
        monitor.record("/customers", HttpMethod.GET, 200, true);
        monitor.record("/account/close", HttpMethod.POST, 200, true);

        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any(), Mockito.any(), Mockito.any());
        monitor.record("/orders", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider).reportSessionLost("user1", "/account/close", "POST");
        Mockito.verify(wfcAuthProvider, Mockito.times(3)).recordSessionHealthy("user1");
    }

    @Test
    void shouldRequireFailuresOnAtLeastTwoKnownGoodOperations() {
        monitor.record("/orders", HttpMethod.GET, 200, true);

        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/never-succeeded", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());
    }

    @Test
    void shouldResetFailureStreakOnSuccessButNotOnOtherErrors() {
        monitor.record("/orders", HttpMethod.GET, 200, true);
        monitor.record("/customers", HttpMethod.GET, 200, true);

        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 200, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 403, true);
        monitor.record("/orders", HttpMethod.GET, 500, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());

        monitor.record("/orders", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider).reportSessionLost("user1");
    }

    @Test
    void shouldReportWithoutSuspectWhenLastSuccessWasSafe() {
        monitor.record("/account/close", HttpMethod.POST, 200, true);
        monitor.record("/orders", HttpMethod.GET, 200, true);
        monitor.record("/customers", HttpMethod.GET, 200, true);

        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider).reportSessionLost("user1");
    }

    @Test
    void shouldTrackCredentialsIndependentlyAndUseNeutralWordingWithoutWfc() {
        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("");
        monitor.record("/orders", HttpMethod.GET, 200, true);
        monitor.record("/customers", HttpMethod.GET, 200, true);
        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("user2");
        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());

        Mockito.when(wfcAuthProvider.getActiveEntryName(Mockito.any(), Mockito.any())).thenReturn("");
        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);
        Mockito.verify(wfcAuthProvider).reportSessionLost("");
    }

    @Test
    void shouldForgetObservationsOnReset() {
        monitor.record("/orders", HttpMethod.GET, 200, true);
        monitor.record("/customers", HttpMethod.GET, 200, true);

        monitor.reset();
        monitor.record("/orders", HttpMethod.GET, 401, true);
        monitor.record("/customers", HttpMethod.GET, 401, true);
        monitor.record("/orders", HttpMethod.GET, 401, true);

        Mockito.verify(wfcAuthProvider, Mockito.never()).reportSessionLost(Mockito.any());
    }
}
