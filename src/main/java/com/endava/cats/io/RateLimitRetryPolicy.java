package com.endava.cats.io;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.exception.CatsExecutionCancelledException;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.RateLimiter;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Retries HTTP calls that receive {@code 429 Too Many Requests} or {@code 503 Service Unavailable} with a
 * {@code Retry-After} header, coordinating waits with {@link RateLimiter} AIMD pacing and execution deadlines.
 */
public final class RateLimitRetryPolicy {

    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final long BASE_BACKOFF_MILLIS = 1_000L;

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RateLimitRetryPolicy.class);
    private final ApiArguments apiArguments;
    private final RateLimiter rateLimiter;
    private final TestCaseListener testCaseListener;
    private final LongSupplier clockMillis;
    private final LongConsumer sleeperMillis;

    public RateLimitRetryPolicy(ApiArguments apiArguments, RateLimiter rateLimiter, TestCaseListener testCaseListener) {
        this(apiArguments, rateLimiter, testCaseListener, System::currentTimeMillis, RateLimitRetryPolicy::sleepMillis);
    }

    RateLimitRetryPolicy(ApiArguments apiArguments, RateLimiter rateLimiter, TestCaseListener testCaseListener,
                         LongSupplier clockMillis, LongConsumer sleeperMillis) {
        this.apiArguments = Objects.requireNonNull(apiArguments, "apiArguments must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.testCaseListener = testCaseListener;
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis must not be null");
        this.sleeperMillis = Objects.requireNonNull(sleeperMillis, "sleeperMillis must not be null");
    }

    @FunctionalInterface
    public interface AttemptCall {
        CatsResponse execute() throws IOException;
    }

    /**
     * Executes an HTTP attempt and retries on rate-limiting responses unless retries are disabled or the request
     * explicitly expects a {@code 429} status code.
     *
     * @param request     the outgoing CATS request
     * @param attemptCall single-attempt supplier
     * @return the final {@link CatsResponse}
     * @throws IOException if the underlying HTTP execution throws an I/O error
     */
    public CatsResponse execute(CatsRequest request, AttemptCall attemptCall) throws IOException {
        CatsResponse response = attemptCall.execute();
        int maxRetries = apiArguments.getMaxRateLimitRetries();
        if (maxRetries <= 0 || (request != null && request.isExpectsRateLimit())) {
            return response;
        }
        return retryIfThrottled(request, attemptCall, response, maxRetries);
    }

    private CatsResponse retryIfThrottled(CatsRequest request, AttemptCall attemptCall,
                                          CatsResponse initialResponse, int maxRetries) throws IOException {
        CatsResponse response = initialResponse;
        int attempt = 0;
        boolean wasThrottled = false;

        while (isThrottleResponse(response)) {
            OptionalLong parsedRetryAfter = parseRetryAfterMillis(response);
            if (response.getResponseCode() == ResponseCodeFamily.STATUS_TOO_MANY_REQUESTS) {
                applyAimdDecrease(parsedRetryAfter.isPresent());
            }
            if (shouldAbortRetry(parsedRetryAfter, attempt, maxRetries)) {
                recordUnretriedThrottleIfFirst(request, wasThrottled);
                return markRateLimitedIf429(response, attempt);
            }
            long waitMs = computeWaitMillis(parsedRetryAfter, attempt);
            if (wouldExceedDeadline(waitMs)) {
                recordUnretriedThrottleIfFirst(request, wasThrottled);
                return markRateLimitedIf429(response, attempt);
            }
            recordThrottleAttempt(request, wasThrottled, waitMs);
            wasThrottled = true;
            waitBeforeRetry(waitMs);
            attempt++;
            response = attemptCall.execute();
        }
        recordCompletionAfterLoop(response, wasThrottled);
        return response;
    }

    private boolean isThrottleResponse(CatsResponse response) {
        if (response == null) {
            return false;
        }
        int code = response.getResponseCode();
        if (code == ResponseCodeFamily.STATUS_TOO_MANY_REQUESTS) {
            return true;
        }
        return code == ResponseCodeFamily.STATUS_SERVICE_UNAVAILABLE && response.getHeader(RETRY_AFTER_HEADER) != null;
    }

    private OptionalLong parseRetryAfterMillis(CatsResponse response) {
        KeyValuePair<String, String> header = response.getHeader(RETRY_AFTER_HEADER);
        return header == null ? OptionalLong.empty() : RetryAfter.parseMillis(header.getValue(), clockMillis.getAsLong());
    }

    private boolean shouldAbortRetry(OptionalLong parsedRetryAfter, int attempt, int maxRetries) {
        long maxRetryAfterMs = maxRetryAfterMillis();
        boolean exceedsCap = parsedRetryAfter.isPresent() && parsedRetryAfter.getAsLong() > maxRetryAfterMs;
        return exceedsCap || attempt >= maxRetries;
    }

    private long computeWaitMillis(OptionalLong parsedRetryAfter, int attempt) {
        long maxRetryAfterMs = maxRetryAfterMillis();
        long shift = Math.min(attempt, 20);
        long backoffMs = Math.min(maxRetryAfterMs, BASE_BACKOFF_MILLIS << shift);
        return parsedRetryAfter.isPresent() ? Math.min(maxRetryAfterMs, parsedRetryAfter.getAsLong()) : backoffMs;
    }

    private long maxRetryAfterMillis() {
        return Math.max(0L, apiArguments.getMaxRetryAfterInSec() * 1_000L);
    }

    private boolean wouldExceedDeadline(long waitMs) {
        return testCaseListener != null && testCaseListener.wouldExceedTimeLimit(waitMs, clockMillis.getAsLong());
    }

    private void applyAimdDecrease(boolean hasRetryAfter) {
        if (rateLimiter.onThrottle(hasRetryAfter)) {
            int effectiveRpm = rateLimiter.getEffectiveRequestsPerMinute();
            logger.info("Request throttled (HTTP 429); effective request rate lowered to {} rpm", effectiveRpm);
            if (testCaseListener != null) {
                testCaseListener.recordEffectiveRate(effectiveRpm, rateLimiter.getConfiguredMaxRequestsPerMinute());
            }
        }
    }

    private void recordCompletionAfterLoop(CatsResponse response, boolean wasThrottled) {
        if (wasThrottled && testCaseListener != null) {
            testCaseListener.recordThrottledRequestRetriedSuccessfully();
        }
        if (response != null && response.isValidErrorCode()
                && response.getResponseCode() != ResponseCodeFamily.STATUS_TOO_MANY_REQUESTS
                && rateLimiter.onSuccess()) {
            int effectiveRpm = rateLimiter.getEffectiveRequestsPerMinute();
            logger.info("Rate limiter recovered after {} consecutive non-429 responses; effective request rate increased to {} rpm",
                    RateLimiter.RECOVERY_THRESHOLD, effectiveRpm);
            if (testCaseListener != null) {
                testCaseListener.recordEffectiveRate(effectiveRpm, rateLimiter.getConfiguredMaxRequestsPerMinute());
            }
        }
    }

    private void recordUnretriedThrottleIfFirst(CatsRequest request, boolean wasThrottled) {
        if (!wasThrottled) {
            recordThrottleAttempt(request, false, 0L);
        }
    }

    private void recordThrottleAttempt(CatsRequest request, boolean wasThrottled, long waitMs) {
        if (testCaseListener == null) {
            return;
        }
        int effectiveRpm = rateLimiter.getEffectiveRequestsPerMinute();
        int configuredRpm = rateLimiter.getConfiguredMaxRequestsPerMinute();
        if (!wasThrottled) {
            testCaseListener.recordRequestThrottled(fallbackOperation(request), waitMs, effectiveRpm, configuredRpm);
        } else {
            testCaseListener.recordThrottledRetryAttempt(waitMs, effectiveRpm, configuredRpm);
        }
    }

    private static String fallbackOperation(CatsRequest request) {
        if (request == null) {
            return "";
        }
        String method = request.getHttpMethod() == null ? "" : request.getHttpMethod();
        String url = request.getUrl() == null ? "" : request.getUrl();
        return method.isEmpty() ? url : method + " " + url;
    }

    private static CatsResponse markRateLimitedIf429(CatsResponse response, int retries) {
        if (response.getResponseCode() == ResponseCodeFamily.STATUS_TOO_MANY_REQUESTS) {
            return response.withRateLimited(retries);
        }
        return response;
    }

    private void waitBeforeRetry(long waitMs) {
        CatsExecutionCancelledException.check();
        if (waitMs > 0) {
            sleeperMillis.accept(waitMs);
        }
        CatsExecutionCancelledException.check();
    }

    private static void sleepMillis(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            CatsExecutionCancelledException.check();
        }
    }
}
