package com.stoxsim.finwiz.service;

/** A cooled provider receives a single recovery probe before normal dispatch resumes. */
final class FinwizCircuit {
    private final java.util.function.LongSupplier nanoTime;
    private long untilNanos;
    private boolean open;
    private boolean probing;

    FinwizCircuit(java.util.function.LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    synchronized int enter() {
        if (!open) return 0;
        if (probing || nanoTime.getAsLong() - untilNanos < 0) return -1;
        probing = true;
        return 1;
    }

    synchronized void open(long seconds) {
        long duration = java.time.Duration.ofSeconds(Math.clamp(seconds, 1, 86400)).toNanos();
        long now = nanoTime.getAsLong();
        if (!open || untilNanos - now < duration) untilNanos = now + duration;
        open = true;
    }

    synchronized void healthy(int permit) {
        if (permit == 1) open = false;
    }

    synchronized void release(int permit) {
        if (permit == 1) probing = false;
    }

    synchronized int state() {
        return !open ? 0 : probing ? 2 : 1;
    }
}
