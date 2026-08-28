package com.stoxsim.subscription.domain;

import java.math.BigDecimal;

public enum SubscriptionPlan {
    FREE("Free", new BigDecimal("0.00"), 0, null, "BASIC", "BASIC", true, false, false),
    PLUS(
        "Plus",
        new BigDecimal("99.00"),
        1,
        new BigDecimal("2500000.0000"),
        "EXPANDED",
        "ADVANCED",
        true,
        false,
        false
    ),
    PRO(
        "Pro",
        new BigDecimal("199.00"),
        5,
        new BigDecimal("10000000.0000"),
        "FULL",
        "ADVANCED_RISK",
        true,
        true,
        true
    );

    public static final BigDecimal STANDARD_COMPETITIVE_CAPITAL_INR =
        new BigDecimal("500000.0000");

    private final String displayName;
    private final BigDecimal monthlyPriceInr;
    private final int maximumSandboxPortfolios;
    private final BigDecimal sandboxCapitalInr;
    private final String finwizTier;
    private final String analyticsTier;
    private final boolean privateLeagues;
    private final boolean scenarioLab;
    private final boolean premiumCompetitions;

    SubscriptionPlan(
        String displayName,
        BigDecimal monthlyPriceInr,
        int maximumSandboxPortfolios,
        BigDecimal sandboxCapitalInr,
        String finwizTier,
        String analyticsTier,
        boolean privateLeagues,
        boolean scenarioLab,
        boolean premiumCompetitions
    ) {
        this.displayName = displayName;
        this.monthlyPriceInr = monthlyPriceInr;
        this.maximumSandboxPortfolios = maximumSandboxPortfolios;
        this.sandboxCapitalInr = sandboxCapitalInr;
        this.finwizTier = finwizTier;
        this.analyticsTier = analyticsTier;
        this.privateLeagues = privateLeagues;
        this.scenarioLab = scenarioLab;
        this.premiumCompetitions = premiumCompetitions;
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal monthlyPriceInr() {
        return monthlyPriceInr;
    }

    public int maximumSandboxPortfolios() {
        return maximumSandboxPortfolios;
    }

    public BigDecimal sandboxCapitalInr() {
        return sandboxCapitalInr;
    }

    public String finwizTier() {
        return finwizTier;
    }

    public String analyticsTier() {
        return analyticsTier;
    }

    public boolean privateLeagues() {
        return privateLeagues;
    }

    public boolean scenarioLab() {
        return scenarioLab;
    }

    public boolean multiplePortfolios() {
        return maximumSandboxPortfolios > 1;
    }

    public boolean premiumCompetitions() {
        return premiumCompetitions;
    }
}
