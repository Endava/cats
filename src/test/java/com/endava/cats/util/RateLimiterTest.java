package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class RateLimiterTest {

    private static final long ONE_SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long HUNDRED_MS_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    @Test
    void shouldCreateRateLimiterWithValidRate() {
        RateLimiter rateLimiter = new RateLimiter(60_000);
        rateLimiter.acquire();
        rateLimiter.acquire();

        assertThat(rateLimiter).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldThrowExceptionForNonPositiveRate(int invalidRate) {
        assertThatThrownBy(() -> new RateLimiter(invalidRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRequestsPerMinute must be > 0");
    }

    @Test
    void shouldAcquirePermitImmediatelyForFirstRequest() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(60, fakeTime::now, fakeTime::park);

        rateLimiter.acquire();

        assertThat(fakeTime.parkedDurations).isEmpty();
    }

    @Test
    void shouldNotBankPermitsBetweenConstructionAndFirstAcquire() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(60, fakeTime::now, fakeTime::park);

        fakeTime.advance(TimeUnit.SECONDS.toNanos(5));
        rateLimiter.acquire();
        rateLimiter.acquire();

        assertThat(fakeTime.parkedDurations).containsExactly(ONE_SECOND_NANOS);
    }

    @Test
    void shouldNotBankUnusedPermitsAcrossSlowPhaseBeforeBurst() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(60, fakeTime::now, fakeTime::park);

        for (int i = 0; i < 3; i++) {
            rateLimiter.acquire();
            fakeTime.advance(TimeUnit.SECONDS.toNanos(2));
        }

        assertThat(fakeTime.parkedDurations).isEmpty();

        rateLimiter.acquire();
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquire();
        }

        assertThat(fakeTime.parkedDurations)
                .hasSize(5)
                .containsOnly(ONE_SECOND_NANOS);
    }

    @Test
    void shouldDelaySecondRequestByRemainingInterval() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(60, fakeTime::now, fakeTime::park);

        rateLimiter.acquire();
        fakeTime.advance(TimeUnit.MILLISECONDS.toNanos(400));
        rateLimiter.acquire();

        assertThat(fakeTime.parkedDurations)
                .containsExactly(TimeUnit.MILLISECONDS.toNanos(600));
    }

    @Test
    void shouldMaintainConsistentRateAcrossBackToBackCalls() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(600, fakeTime::now, fakeTime::park);

        for (int i = 0; i < 4; i++) {
            rateLimiter.acquire();
        }

        assertThat(fakeTime.parkedDurations)
                .containsExactly(HUNDRED_MS_NANOS, HUNDRED_MS_NANOS, HUNDRED_MS_NANOS);
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0L);
        RateLimiter rateLimiter = new RateLimiter(600, clock::get, clock::addAndGet);
        Thread[] threads = new Thread[3];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(rateLimiter::acquire);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(threads).allMatch(t -> !t.isAlive());
        assertThat(clock.get()).isEqualTo(2 * HUNDRED_MS_NANOS);
    }

    @Test
    void shouldStartAimdDecreaseFromObservedIntervalAndDoubleWhenRetryAfterAbsent() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(10_000, fakeTime::now, fakeTime::park);

        for (int i = 0; i < 4; i++) {
            rateLimiter.acquire();
            fakeTime.advance(HUNDRED_MS_NANOS);
        }

        boolean changed = rateLimiter.onThrottle(false);

        assertThat(changed).isTrue();
        assertThat(rateLimiter.getEffectiveIntervalNanos()).isEqualTo(2 * HUNDRED_MS_NANOS);
        assertThat(rateLimiter.getEffectiveRequestsPerMinute()).isEqualTo(300);

        fakeTime.parkedDurations.clear();
        rateLimiter.acquire();
        assertThat(fakeTime.parkedDurations).containsExactly(HUNDRED_MS_NANOS);
    }

    @Test
    void shouldAdoptObservedIntervalOn429WithRetryAfterAndDoubleIfAlreadyAtObservedRate() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter defaultLimiter = new RateLimiter(10_000, fakeTime::now, fakeTime::park);

        for (int i = 0; i < 3; i++) {
            defaultLimiter.acquire();
            fakeTime.advance(HUNDRED_MS_NANOS);
        }

        assertThat(defaultLimiter.onThrottle(true)).isTrue();
        assertThat(defaultLimiter.getEffectiveIntervalNanos()).isEqualTo(HUNDRED_MS_NANOS);
        assertThat(defaultLimiter.getEffectiveRequestsPerMinute()).isEqualTo(600);

        assertThat(defaultLimiter.onThrottle(true)).isTrue();
        assertThat(defaultLimiter.getEffectiveIntervalNanos()).isEqualTo(2 * HUNDRED_MS_NANOS);
        assertThat(defaultLimiter.getEffectiveRequestsPerMinute()).isEqualTo(300);
    }

    @Test
    void shouldRecoverAdditivelyEveryTwentyConsecutiveNon429Responses() {
        FakeTime fakeTime = new FakeTime();
        RateLimiter rateLimiter = new RateLimiter(600, fakeTime::now, fakeTime::park);

        rateLimiter.acquire();
        rateLimiter.acquire();
        rateLimiter.onThrottle(false);

        assertThat(rateLimiter.getEffectiveIntervalNanos()).isEqualTo(2 * HUNDRED_MS_NANOS);

        for (int i = 1; i < RateLimiter.RECOVERY_THRESHOLD; i++) {
            assertThat(rateLimiter.onSuccess()).isFalse();
        }
        assertThat(rateLimiter.onSuccess()).isTrue();
        assertThat(rateLimiter.getEffectiveIntervalNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(190));

        for (int i = 0; i < 10; i++) {
            rateLimiter.onSuccess();
        }
        rateLimiter.onThrottle(false);
        for (int i = 1; i < RateLimiter.RECOVERY_THRESHOLD; i++) {
            assertThat(rateLimiter.onSuccess()).isFalse();
        }
        assertThat(rateLimiter.onSuccess()).isTrue();
        assertThat(rateLimiter.getEffectiveIntervalNanos()).isLessThan(TimeUnit.MILLISECONDS.toNanos(380));
    }

    private static final class FakeTime {
        private long currentNanos = 1_000_000_000L;
        private final List<Long> parkedDurations = new ArrayList<>();

        long now() {
            return currentNanos;
        }

        void advance(long deltaNanos) {
            currentNanos += deltaNanos;
        }

        void park(long waitNanos) {
            parkedDurations.add(waitNanos);
            currentNanos += waitNanos;
        }
    }
}
