package com.stoxsim.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarketRegionTest {

    @Test
    void mapsEachRegionToItsOwnCurrency() {
        assertThat(MarketRegion.INDIA.currency()).isEqualTo("INR");
        assertThat(MarketRegion.UNITED_STATES.currency()).isEqualTo("USD");
    }
}
