package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class RateLimiterTest {

    @Test
    void shouldCreateRateLimiterWithValidRate() {
        RateLimiter rateLimiter = new RateLimiter(60);

        assertThat(rateLimiter).isNotNull();
    }

    @Test
    void shouldThrowExceptionForZeroRate() {
        assertThatThrownBy(() -> new RateLimiter(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRequestsPerMinute must be > 0");
    }

    @Test
    void shouldThrowExceptionForNegativeRate() {
        assertThatThrownBy(() -> new RateLimiter(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRequestsPerMinute must be > 0");
    }

    @Test
    void shouldAcquirePermitImmediatelyForFirstRequest() {
        RateLimiter rateLimiter = new RateLimiter(60);
        long start = System.nanoTime();

        rateLimiter.acquire();

        long elapsed = System.nanoTime() - start;
        assertThat(elapsed).isLessThan(100_000_000L);
    }

    @Test
    void shouldDelaySecondRequest() {
        RateLimiter rateLimiter = new RateLimiter(600);

        rateLimiter.acquire();
        long start = System.nanoTime();
        rateLimiter.acquire();
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed).isGreaterThan(50_000_000L);
    }

    @Test
    void shouldRespectRateLimit() {
        RateLimiter rateLimiter = new RateLimiter(600);
        long start = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            rateLimiter.acquire();
        }

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isGreaterThanOrEqualTo(200L);
    }

    @Test
    void shouldHandleHighRate() {
        RateLimiter rateLimiter = new RateLimiter(6000);

        long start = System.nanoTime();
        rateLimiter.acquire();
        rateLimiter.acquire();
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed).isLessThan(50_000_000L);
    }

    @Test
    void shouldHandleLowRate() {
        RateLimiter rateLimiter = new RateLimiter(60);

        rateLimiter.acquire();
        long start = System.nanoTime();
        rateLimiter.acquire();
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed).isGreaterThan(900_000_000L);
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        RateLimiter rateLimiter = new RateLimiter(600);
        Thread[] threads = new Thread[3];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(rateLimiter::acquire);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(threads).allMatch(t -> !t.isAlive());
    }

    @Test
    void shouldMaintainConsistentRate() {
        RateLimiter rateLimiter = new RateLimiter(600);
        long start = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            rateLimiter.acquire();
        }

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isBetween(180L, 350L);
    }
}
