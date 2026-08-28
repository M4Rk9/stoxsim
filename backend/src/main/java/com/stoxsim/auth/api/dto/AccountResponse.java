package com.stoxsim.auth.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.subscription.domain.SubscriptionPlan;

public record AccountResponse(
    UUID id,
    MarketRegion marketRegion,
    AccountKind accountKind,
    SubscriptionPlan sandboxPlan,
    int sandboxSlot,
    String accountLabel,
    String currency,
    BigDecimal startingCapital,
    BigDecimal availableCash,
    BigDecimal blockedCash,
    BigDecimal realizedProfitLoss,
    boolean active,
    boolean leaderboardEligible
) {
    public static AccountResponse from(VirtualAccount account) {
        return new AccountResponse(
            account.getId(),
            account.getMarketRegion(),
            account.getAccountKind(),
            account.getSandboxPlan(),
            account.getSandboxSlot(),
            account.getAccountLabel(),
            account.getCurrency(),
            account.getStartingCapital(),
            account.getAvailableCash(),
            account.getBlockedCash(),
            account.getRealizedProfitLoss(),
            account.isActive(),
            account.isLeaderboardEligible()
        );
    }
}
