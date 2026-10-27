package com.endava.cats.auth.session;

import com.endava.cats.auth.wfc.WfcAuthProvider;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detects mid-run session loss from passive response signals and triggers credential recovery.
 * <p>
 * Only requests sent with untouched credentials, method, and path are observed. A session is considered lost after
 * {@value #MAX_CONSECUTIVE_UNEXPECTED_UNAUTHORIZED} consecutive {@code 401} responses on at least
 * {@value #MIN_DISTINCT_FAILING_OPERATIONS} operations that previously succeeded with the same credentials.
 * When rotating away from a dead credential entry, the last state-changing operation that succeeded before the
 * failure streak is kept quarantined on that entry so it cannot invalidate the replacement entry as well.
 */
@ApplicationScoped
public class SessionHealthMonitor {
    static final int MAX_CONSECUTIVE_UNEXPECTED_UNAUTHORIZED = 3;
    static final int MIN_DISTINCT_FAILING_OPERATIONS = 2;
    private static final int UNAUTHORIZED = 401;
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.TRACE);

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(SessionHealthMonitor.class);
    private final WfcAuthProvider wfcAuthProvider;
    private final Map<String, CredentialHealth> healthByCredential = new HashMap<>();

    private record Operation(String path, HttpMethod method) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static final class CredentialHealth {
        private final Set<Operation> knownGoodOperations = new HashSet<>();
        private final Set<Operation> failingOperations = new HashSet<>();
        private int consecutiveUnexpectedUnauthorized;
        private Operation lastSuccessfulMutation;

        private void resetFailures() {
            failingOperations.clear();
            consecutiveUnexpectedUnauthorized = 0;
        }
    }

    /**
     * Creates the monitor.
     *
     * @param wfcAuthProvider WFC credential provider used for recovery
     */
    @Inject
    public SessionHealthMonitor(WfcAuthProvider wfcAuthProvider) {
        this.wfcAuthProvider = wfcAuthProvider;
    }

    /**
     * Records the outcome of a completed request.
     *
     * @param path              contract path
     * @param method            HTTP method
     * @param statusCode        HTTP response code
     * @param credentialsIntact true when the request carried the configured credentials unmodified
     */
    public synchronized void record(String path, HttpMethod method, int statusCode, boolean credentialsIntact) {
        if (!credentialsIntact || method == null) {
            return;
        }
        String credential = wfcAuthProvider.getActiveEntryName(path, method.name());
        CredentialHealth health = healthByCredential.computeIfAbsent(credential, _ -> new CredentialHealth());
        Operation operation = new Operation(path, method);
        if (ResponseCodeFamily.is2xxCode(statusCode)) {
            recordSuccess(credential, health, operation);
        } else if (statusCode == UNAUTHORIZED && health.knownGoodOperations.contains(operation)) {
            recordUnexpectedUnauthorized(credential, health, operation);
        }
    }

    /**
     * Forgets all observations from a previous run.
     */
    public synchronized void reset() {
        healthByCredential.clear();
    }

    private void recordSuccess(String credential, CredentialHealth health, Operation operation) {
        health.knownGoodOperations.add(operation);
        health.resetFailures();
        wfcAuthProvider.recordSessionHealthy(credential);
        health.lastSuccessfulMutation = SAFE_METHODS.contains(operation.method()) ? null : operation;
    }

    private void recordUnexpectedUnauthorized(String credential, CredentialHealth health, Operation operation) {
        health.consecutiveUnexpectedUnauthorized++;
        health.failingOperations.add(operation);
        if (health.consecutiveUnexpectedUnauthorized < MAX_CONSECUTIVE_UNEXPECTED_UNAUTHORIZED ||
                health.failingOperations.size() < MIN_DISTINCT_FAILING_OPERATIONS) {
            return;
        }
        Operation suspect = health.lastSuccessfulMutation;
        logger.warning("Session for {} appears invalidated{}",
                credential.isEmpty() ? "the supplied credentials" : "WFC Auth entry " + credential,
                suspect == null ? "" : " after " + suspect + " succeeded");
        health.lastSuccessfulMutation = null;
        health.resetFailures();
        if (suspect != null) {
            wfcAuthProvider.reportSessionLost(credential, suspect.path(), suspect.method().name());
        } else {
            wfcAuthProvider.reportSessionLost(credential);
        }
    }
}
