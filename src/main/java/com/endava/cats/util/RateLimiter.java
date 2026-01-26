package com.endava.cats.util;

import java.util.concurrent.locks.LockSupport;

/**
 * Rate limiter for http calls.
 */
public final class RateLimiter {

    private final long intervalNanos;
    private long nextAllowedTimeNanos;

    /**
     * Creates a new instance.
     *
     * @param maxRequestsPerMinute the maximum number of requests per minute
     */
    public RateLimiter(int maxRequestsPerMinute) {
        if (maxRequestsPerMinute <= 0) {
            throw new IllegalArgumentException("maxRequestsPerMinute must be > 0");
        }
        double permitsPerSecond = maxRequestsPerMinute / 60.0;

        // nanos between permits (cap at 1ns to avoid division edge cases)
        this.intervalNanos = Math.max(1L, (long) (1_000_000_000L / permitsPerSecond));
        this.nextAllowedTimeNanos = System.nanoTime();
    }

    /**
     * Acquires a permit.
     */
    public synchronized void acquire() {
        long now = System.nanoTime();
        long waitNanos = nextAllowedTimeNanos - now;

        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }

        nextAllowedTimeNanos += intervalNanos;
    }
}
