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
