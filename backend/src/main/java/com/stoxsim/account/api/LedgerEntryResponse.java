package com.stoxsim.account.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.account.domain.AccountLedgerEntry;

public record LedgerEntryResponse(
    UUID id,
    AccountLedgerEntry.EntryType entryType,
    AccountLedgerEntry.Direction direction,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String description,
    Instant createdAt
) {
    public static LedgerEntryResponse from(AccountLedgerEntry entry) {
        return new LedgerEntryResponse(
            entry.getId(),
            entry.getEntryType(),
            entry.getDirection(),
            entry.getAmount(),
            entry.getBalanceAfter(),
            entry.getDescription(),
            entry.getCreatedAt()
        );
    }
}
