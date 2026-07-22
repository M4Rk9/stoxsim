package com.stoxsim.market.domain;

public enum MarketRegion {
    INDIA("INR"),
    UNITED_STATES("USD");

    private final String currency;

    MarketRegion(String currency) {
        this.currency = currency;
    }

    public String currency() {
        return currency;
    }
}
