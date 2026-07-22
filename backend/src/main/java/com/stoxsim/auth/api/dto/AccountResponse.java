package com.stoxsim.auth.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.market.domain.MarketRegion;

public record AccountResponse(
    UUID id,
    MarketRegion marketRegion,
    String currency,
    BigDecimal availableCash,
    BigDecimal blockedCash,
    BigDecimal realizedProfitLoss
) {
    public static AccountResponse from(VirtualAccount account) {
        return new AccountResponse(
            account.getId(),
            account.getMarketRegion(),
            account.getCurrency(),
            account.getAvailableCash(),
            account.getBlockedCash(),
            account.getRealizedProfitLoss()
        );
    }
}
