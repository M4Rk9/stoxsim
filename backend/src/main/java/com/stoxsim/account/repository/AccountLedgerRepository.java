package com.stoxsim.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.account.domain.AccountLedgerEntry;
import com.stoxsim.market.domain.MarketRegion;

public interface AccountLedgerRepository extends JpaRepository<AccountLedgerEntry, UUID> {

    List<AccountLedgerEntry> findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(
        UUID userId,
        MarketRegion marketRegion
    );
}
