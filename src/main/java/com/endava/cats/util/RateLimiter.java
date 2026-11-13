package com.endava.cats.util;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Rate limiter for http calls.
 */
public final class RateLimiter {

    private final long intervalNanos;
    private final LongSupplier clock;
    private final LongConsumer parker;
    private long nextAllowedTimeNanos;

    /**
     * Creates a new instance.
     *
     * @param maxRequestsPerMinute the maximum number of requests per minute
     */
    public RateLimiter(int maxRequestsPerMinute) {
        this(maxRequestsPerMinute, System::nanoTime, LockSupport::parkNanos);
    }

    RateLimiter(int maxRequestsPerMinute, LongSupplier clock, LongConsumer parker) {
        if (maxRequestsPerMinute <= 0) {
            throw new IllegalArgumentException("maxRequestsPerMinute must be > 0");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.parker = Objects.requireNonNull(parker, "parker must not be null");
        double permitsPerSecond = maxRequestsPerMinute / 60.0;

        // nanos between permits (cap at 1ns to avoid division edge cases)
        this.intervalNanos = Math.max(1L, (long) (1_000_000_000L / permitsPerSecond));
        this.nextAllowedTimeNanos = Long.MIN_VALUE;
    }

    /**
     * Acquires a permit.
     */
    public synchronized void acquire() {
        long now = clock.getAsLong();
        long slot = Math.max(nextAllowedTimeNanos, now);
        parkIfPositive(slot - now);
        nextAllowedTimeNanos = slot + intervalNanos;
    }

    private void parkIfPositive(long waitNanos) {
        if (waitNanos > 0) {
            parker.accept(waitNanos);
        }
    }
}
