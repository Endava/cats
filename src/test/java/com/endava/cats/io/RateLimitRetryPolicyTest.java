package com.endava.cats.io;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.exception.CatsExecutionCancelledException;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.RateLimiter;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class RateLimitRetryPolicyTest {
    private ApiArguments arguments;
    private RateLimiter limiter;
    private TestCaseListener listener;
    private RateLimitRetryPolicy.AttemptCall attempt;
    private List<Long> waits;
    private RateLimitRetryPolicy policy;

    @BeforeEach
    void setUp() {
        arguments = mock(ApiArguments.class);
        limiter = mock(RateLimiter.class);
        listener = mock(TestCaseListener.class);
        attempt = mock(RateLimitRetryPolicy.AttemptCall.class);
        waits = new ArrayList<>();
        when(arguments.getMaxRateLimitRetries()).thenReturn(3);
        when(arguments.getMaxRetryAfterInSec()).thenReturn(30);
        when(limiter.getEffectiveRequestsPerMinute()).thenReturn(60);
        when(limiter.getConfiguredMaxRequestsPerMinute()).thenReturn(120);
        policy = new RateLimitRetryPolicy(arguments, limiter, listener, () -> 10_000L, waits::add);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 200, 503, 999})
    void shouldReturnNonThrottleResponsesUnchangedIncludingNull(int code) throws IOException {
        CatsResponse response = code == 0 ? null : response(code, null);
        when(attempt.execute()).thenReturn(response);

        assertThat(policy.execute(null, attempt)).isSameAs(response);
        verify(attempt).execute();
        assertThat(waits).isEmpty();
        verifyNoInteractions(listener);
    }

    @ParameterizedTest
    @CsvSource({"0, false", "-1, false", "3, true"})
    void shouldBypassRetriesWhenDisabledOrRateLimitingIsExpected(int retries, boolean expected429) throws IOException {
        when(arguments.getMaxRateLimitRetries()).thenReturn(retries);
        CatsRequest request = CatsRequest.builder().expectsRateLimit(expected429).build();
        CatsResponse response = response(429, null);
        when(attempt.execute()).thenReturn(response);

        assertThat(policy.execute(request, attempt)).isSameAs(response);
        verify(attempt).execute();
        verifyNoInteractions(limiter, listener);
        assertThat(waits).isEmpty();
    }

    @Test
    void shouldCapExponentialBackoffAndRecordEachRetryWithoutDoubleCountingTheRequest() throws IOException {
        when(arguments.getMaxRetryAfterInSec()).thenReturn(3);
        when(attempt.execute()).thenReturn(response(429, null));

        CatsResponse result = policy.execute(null, attempt);

        assertThat(result.isRateLimited()).isTrue();
        assertThat(result.getRateLimitRetries()).isEqualTo(3);
        assertThat(waits).containsExactly(1_000L, 2_000L, 3_000L);
        verify(attempt, times(4)).execute();
        verify(listener).recordRequestThrottled("", 1_000L, 60, 120);
        verify(listener).recordThrottledRetryAttempt(2_000L, 60, 120);
        verify(listener).recordThrottledRetryAttempt(3_000L, 60, 120);
        verify(listener, never()).recordThrottledRequestRetriedSuccessfully();
    }

    @Test
    void shouldPublishRateDecreaseAndRecoveryOnASuccessfulRetry() throws IOException {
        when(limiter.onThrottle(false)).thenReturn(true);
        when(limiter.onSuccess()).thenReturn(true);
        when(limiter.getEffectiveRequestsPerMinute()).thenReturn(60, 60, 70);
        CatsResponse success = response(200, null);
        when(attempt.execute()).thenReturn(response(429, null), success);
        CatsRequest request = CatsRequest.builder().httpMethod("GET").url("https://example.test/pets").build();

        assertThat(policy.execute(request, attempt)).isSameAs(success);
        assertThat(waits).containsExactly(1_000L);
        verify(listener).recordRequestThrottled("GET https://example.test/pets", 1_000L, 60, 120);
        verify(listener).recordThrottledRequestRetriedSuccessfully();
        verify(listener).recordEffectiveRate(60, 120);
        verify(listener).recordEffectiveRate(70, 120);
    }

    @Test
    void shouldRetryWithoutAListenerAndWithoutWaitingForAZeroRetryAfter() throws IOException {
        policy = new RateLimitRetryPolicy(arguments, limiter, null, () -> 10_000L, waits::add);
        when(limiter.onThrottle(true)).thenReturn(true);
        when(limiter.onSuccess()).thenReturn(true);
        CatsResponse success = response(200, null);
        when(attempt.execute()).thenReturn(response(429, "0"), success);

        assertThat(policy.execute(null, attempt)).isSameAs(success);
        verify(attempt, times(2)).execute();
        verify(limiter).onThrottle(true);
        verify(limiter).onSuccess();
        assertThat(waits).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void shouldNotWaitOrRetryBeyondTheSessionDeadline(int code) throws IOException {
        CatsResponse throttled = response(code, "2");
        when(attempt.execute()).thenReturn(throttled);
        when(listener.wouldExceedTimeLimit(2_000L, 10_000L)).thenReturn(true);

        CatsResponse result = policy.execute(null, attempt);

        assertThat(result.getResponseCode()).isEqualTo(code);
        assertThat(result.isRateLimited()).isEqualTo(code == 429);
        assertThat(result.getRateLimitRetries()).isZero();
        if (code == 503) {
            assertThat(result).isSameAs(throttled);
        }
        assertThat(waits).isEmpty();
        verify(attempt).execute();
        verify(listener).recordRequestThrottled("", 0L, 60, 120);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL, NULL, ''", "NULL, /pets, /pets", "GET, NULL, 'GET '", "'', /pets, /pets"})
    void shouldDescribeIncompleteRequestsWhenRetryAfterExceedsTheCap(String method, String url, String expectedOperation) throws IOException {
        when(attempt.execute()).thenReturn(response(429, "31"));

        CatsResponse result = policy.execute(CatsRequest.builder().httpMethod(method).url(url).build(), attempt);

        assertThat(result.isRateLimited()).isTrue();
        assertThat(result.getRateLimitRetries()).isZero();
        assertThat(waits).isEmpty();
        verify(attempt).execute();
        verify(listener).recordRequestThrottled(expectedOperation, 0L, 60, 120);
    }

    @Test
    void shouldRetry503WithRetryAfterWithoutChangingAimdRate() throws IOException {
        CatsResponse success = response(200, null);
        when(attempt.execute()).thenReturn(response(503, "2"), success);

        assertThat(policy.execute(null, attempt)).isSameAs(success);
        assertThat(waits).containsExactly(2_000L);
        verify(limiter, never()).onThrottle(anyBoolean());
        verify(listener).recordThrottledRequestRetriedSuccessfully();
    }

    @Test
    void shouldPreserve503WhenRetriesAreExhausted() throws IOException {
        when(arguments.getMaxRateLimitRetries()).thenReturn(1);
        CatsResponse unavailable = response(503, "0");
        when(attempt.execute()).thenReturn(unavailable);

        assertThat(policy.execute(null, attempt)).isSameAs(unavailable);
        verify(attempt, times(2)).execute();
        verify(listener).recordRequestThrottled("", 0L, 60, 120);
        verify(listener, never()).recordThrottledRequestRetriedSuccessfully();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPropagateIoFailuresWithoutRetryingThem(boolean duringRetry) throws IOException {
        IOException failure = new IOException("connection failed");
        if (duringRetry) {
            when(attempt.execute()).thenReturn(response(429, "0")).thenThrow(failure);
        } else {
            when(attempt.execute()).thenThrow(failure);
        }

        assertThatThrownBy(() -> policy.execute(null, attempt)).isSameAs(failure);
        verify(attempt, times(duringRetry ? 2 : 1)).execute();
        verify(listener, never()).recordThrottledRequestRetriedSuccessfully();
    }

    @Test
    void shouldCancelBeforeWaitingWhenTheWorkerIsInterrupted() throws IOException {
        when(attempt.execute()).thenReturn(response(429, null));
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> policy.execute(null, attempt)).isInstanceOf(CatsExecutionCancelledException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(attempt).execute();
        assertThat(waits).isEmpty();
    }

    @Test
    void shouldCancelAfterAnInterruptedWaitWithoutSendingAnotherRequest() throws IOException {
        when(attempt.execute()).thenReturn(response(429, null));
        policy = new RateLimitRetryPolicy(arguments, limiter, listener, () -> 10_000L, wait -> Thread.currentThread().interrupt());
        try {
            assertThatThrownBy(() -> policy.execute(null, attempt)).isInstanceOf(CatsExecutionCancelledException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(attempt).execute();
        verify(listener, never()).recordThrottledRequestRetriedSuccessfully();
    }

    private static CatsResponse response(int code, String retryAfter) {
        return CatsResponse.builder().responseCode(code)
                .headers(retryAfter == null ? List.of() : List.of(new KeyValuePair<>("Retry-After", retryAfter))).build();
    }
}
