package com.stoxsim.market.provider.sec;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class SecRequestThrottle {

    static final int SEC_HARD_MAX_REQUESTS_PER_SECOND = 10;

    private final long minimumIntervalNanos;
    private final LongSupplier nanoTime;
    private final LongConsumer pause;
    private long nextPermitNanos;

    SecRequestThrottle(int maxRequestsPerSecond) {
        this(maxRequestsPerSecond, System::nanoTime, LockSupport::parkNanos);
    }

    SecRequestThrottle(
        int maxRequestsPerSecond,
        LongSupplier nanoTime,
        LongConsumer pause
    ) {
        if (maxRequestsPerSecond < 1
            || maxRequestsPerSecond > SEC_HARD_MAX_REQUESTS_PER_SECOND) {
            throw new IllegalArgumentException(
                "SEC request rate must be between 1 and "
                    + SEC_HARD_MAX_REQUESTS_PER_SECOND
                    + " requests per second"
            );
        }
        this.minimumIntervalNanos = Math.ceilDiv(
            1_000_000_000L,
            maxRequestsPerSecond
        );
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.pause = Objects.requireNonNull(pause);
    }

    synchronized void acquire() {
        long now = nanoTime.getAsLong();
        long remaining = nextPermitNanos - now;
        while (remaining > 0) {
            pause.accept(remaining);
            now = nanoTime.getAsLong();
            remaining = nextPermitNanos - now;
        }
        nextPermitNanos = Math.max(now, nextPermitNanos) + minimumIntervalNanos;
    }
}
