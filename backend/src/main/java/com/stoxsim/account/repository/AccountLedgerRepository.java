package com.stoxsim.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.account.domain.AccountLedgerEntry;
import com.stoxsim.market.domain.MarketRegion;

public interface AccountLedgerRepository extends JpaRepository<AccountLedgerEntry, UUID> {

    @Query("""
        SELECT entry
        FROM AccountLedgerEntry entry
        WHERE entry.account.user.id = :userId
          AND entry.account.marketRegion = :marketRegion
        ORDER BY entry.createdAt DESC
        """)
    List<AccountLedgerEntry> findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );
}
