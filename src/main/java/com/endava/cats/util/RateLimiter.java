package com.endava.cats.util;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Rate limiter for HTTP calls with AIMD (Additive Increase, Multiplicative Decrease) adaptive pacing.
 */
public final class RateLimiter {

    public static final int RECOVERY_THRESHOLD = 20;
    private static final double RECOVERY_FRACTION = 0.10;
    private static final double EWMA_ALPHA = 0.25;
    private static final long MINUTE_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final int configuredMaxRequestsPerMinute;
    private final long configuredIntervalNanos;
    private final LongSupplier clock;
    private final LongConsumer parker;
    private long effectiveIntervalNanos;
    private long nextAllowedTimeNanos;
    private long lastAcquireTimeNanos;
    private double observedIntervalEwmaNanos;
    private int consecutiveNonThrottled;

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
        this.configuredMaxRequestsPerMinute = maxRequestsPerMinute;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.parker = Objects.requireNonNull(parker, "parker must not be null");
        double permitsPerSecond = maxRequestsPerMinute / 60.0;

        // nanos between permits (cap at 1ns to avoid division edge cases)
        this.configuredIntervalNanos = Math.max(1L, (long) (1_000_000_000L / permitsPerSecond));
        this.effectiveIntervalNanos = this.configuredIntervalNanos;
        this.nextAllowedTimeNanos = Long.MIN_VALUE;
        this.lastAcquireTimeNanos = Long.MIN_VALUE;
    }

    /**
     * Acquires a permit.
     */
    public synchronized void acquire() {
        long now = clock.getAsLong();
        long slot = Math.max(nextAllowedTimeNanos, now);
        parkIfPositive(slot - now);
        recordObservedInterval(slot);
        nextAllowedTimeNanos = slot + effectiveIntervalNanos;
    }

    /**
     * Adjusts the effective inter-request interval on an HTTP 429 response when {@code Retry-After} is absent,
     * doubling the interval starting from the observed inter-request interval.
     *
     * @return {@code true} if the effective interval changed
     */
    public synchronized boolean onThrottle() {
        return onThrottle(false);
    }

    /**
     * Adjusts the effective inter-request interval on an HTTP 429 response, starting from the observed
     * inter-request interval EWMA rather than the configured interval.
     *
     * @param hasRetryAfter whether the 429 response included a valid {@code Retry-After} header
     * @return {@code true} if the effective interval changed
     */
    public synchronized boolean onThrottle(boolean hasRetryAfter) {
        consecutiveNonThrottled = 0;
        long previous = effectiveIntervalNanos;
        long observedBase = resolveObservedBaseInterval();
        long candidate = hasRetryAfter ? observedBase : multiplyCapped(observedBase, 2);
        if (candidate <= previous && hasRetryAfter) {
            candidate = multiplyCapped(observedBase, 2);
        }
        effectiveIntervalNanos = Math.max(configuredIntervalNanos, candidate);
        if (lastAcquireTimeNanos != Long.MIN_VALUE) {
            nextAllowedTimeNanos = Math.max(nextAllowedTimeNanos, lastAcquireTimeNanos + effectiveIntervalNanos);
        }
        lastAcquireTimeNanos = Long.MIN_VALUE;
        observedIntervalEwmaNanos = 0;
        return effectiveIntervalNanos != previous;
    }

    /**
     * Records a non-429 response. Every {@link #RECOVERY_THRESHOLD} consecutive non-429 responses shrink
     * {@code effectiveIntervalNanos} additively by 10% of its distance to {@code configuredIntervalNanos}.
     *
     * @return {@code true} if the effective interval shrank
     */
    public synchronized boolean onSuccess() {
        if (effectiveIntervalNanos <= configuredIntervalNanos) {
            consecutiveNonThrottled = 0;
            return false;
        }
        consecutiveNonThrottled++;
        if (consecutiveNonThrottled < RECOVERY_THRESHOLD) {
            return false;
        }
        consecutiveNonThrottled = 0;
        long distance = effectiveIntervalNanos - configuredIntervalNanos;
        long step = Math.max(1L, Math.round(distance * RECOVERY_FRACTION));
        long previous = effectiveIntervalNanos;
        effectiveIntervalNanos = Math.max(configuredIntervalNanos, effectiveIntervalNanos - step);
        observedIntervalEwmaNanos = 0;
        return effectiveIntervalNanos != previous;
    }

    public int getConfiguredMaxRequestsPerMinute() {
        return configuredMaxRequestsPerMinute;
    }

    public long getConfiguredIntervalNanos() {
        return configuredIntervalNanos;
    }

    public synchronized long getEffectiveIntervalNanos() {
        return effectiveIntervalNanos;
    }

    public synchronized int getEffectiveRequestsPerMinute() {
        if (effectiveIntervalNanos <= configuredIntervalNanos) {
            return configuredMaxRequestsPerMinute;
        }
        return Math.max(1, (int) Math.round((double) MINUTE_NANOS / effectiveIntervalNanos));
    }

    private long resolveObservedBaseInterval() {
        long observedNanos = observedIntervalEwmaNanos > 0
                ? Math.round(observedIntervalEwmaNanos)
                : configuredIntervalNanos;
        return Math.max(effectiveIntervalNanos, Math.max(configuredIntervalNanos, observedNanos));
    }

    private static long multiplyCapped(long value, int factor) {
        if (value >= MINUTE_NANOS / factor) {
            return MINUTE_NANOS;
        }
        return value * factor;
    }

    private void recordObservedInterval(long acquireTimeNanos) {
        if (lastAcquireTimeNanos != Long.MIN_VALUE) {
            long sampleNanos = Math.max(1L, acquireTimeNanos - lastAcquireTimeNanos);
            observedIntervalEwmaNanos = observedIntervalEwmaNanos <= 0
                    ? sampleNanos
                    : observedIntervalEwmaNanos * (1.0 - EWMA_ALPHA) + sampleNanos * EWMA_ALPHA;
        }
        lastAcquireTimeNanos = acquireTimeNanos;
    }

    private void parkIfPositive(long waitNanos) {
        if (waitNanos > 0) {
            parker.accept(waitNanos);
        }
    }
}
