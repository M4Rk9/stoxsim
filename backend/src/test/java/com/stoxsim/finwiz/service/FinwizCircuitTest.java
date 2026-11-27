package com.stoxsim.finwiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FinwizCircuitTest {
    @Test void onlyOneRecoveryProbeIsAdmittedAndFailureReopensCircuit() {
        var clock = new AtomicLong();
        var circuit = new FinwizCircuit(clock::get);
        assertThat(circuit.enter()).isZero();
        circuit.open(2);
        assertThat(circuit.enter()).isNegative();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        int probe = circuit.enter();
        assertThat(probe).isPositive();
        assertThat(circuit.enter()).isNegative();
        circuit.open(1);
        circuit.release(probe);
        assertThat(circuit.enter()).isNegative();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        probe = circuit.enter();
        circuit.healthy(probe);
        circuit.release(probe);
        assertThat(circuit.enter()).isZero();
    }
}
