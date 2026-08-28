package com.stoxsim.trade.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.trade.domain.Trade;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    @Query("""
        SELECT trade
        FROM Trade trade
        WHERE trade.account.user.id = :userId
          AND trade.account.marketRegion = :marketRegion
          AND trade.account.accountKind = com.stoxsim.account.domain.AccountKind.STANDARD
        ORDER BY trade.executedAt DESC
        """)
    List<Trade> findAllByAccountUserIdAndAccountMarketRegionOrderByExecutedAtDesc(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );

    @Query("""
        SELECT COUNT(trade)
        FROM Trade trade
        WHERE trade.account.user.id = :userId
          AND trade.account.marketRegion = :marketRegion
          AND trade.account.accountKind = com.stoxsim.account.domain.AccountKind.STANDARD
          AND trade.executedAt >= :from
          AND trade.executedAt < :until
        """)
    long countForReport(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion,
        @Param("from") Instant from,
        @Param("until") Instant until
    );

    @Query("""
        SELECT COUNT(trade)
        FROM Trade trade
        WHERE trade.account.user.id = :userId
          AND trade.account.marketRegion = :marketRegion
          AND trade.account.accountKind = com.stoxsim.account.domain.AccountKind.STANDARD
        """)
    long countByUserIdAndMarketRegion(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );
}
