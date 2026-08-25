package com.stoxsim.market.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SecRequestThrottleTest {

    @Test
    void spacesRequestsBelowTheSecHardLimit() {
        AtomicLong now = new AtomicLong(1_000);
        List<Long> pauses = new ArrayList<>();
        SecRequestThrottle throttle = new SecRequestThrottle(
            8,
            now::get,
            nanos -> {
                pauses.add(nanos);
                now.addAndGet(nanos);
            }
        );

        throttle.acquire();
        throttle.acquire();
        throttle.acquire();

        assertThat(pauses).containsExactly(125_000_000L, 125_000_000L);
    }

    @Test
    void rejectsRatesAboveTheSecFairAccessCeiling() {
        assertThatThrownBy(() -> new SecRequestThrottle(11))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 10");
    }

    @Test
    void rejectsDisabledPacing() {
        assertThatThrownBy(() -> new SecRequestThrottle(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 10");
    }
}
