package com.stoxsim.trade.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.trade.domain.Trade;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    List<Trade> findAllByAccountUserIdAndAccountMarketRegionOrderByExecutedAtDesc(
        UUID userId,
        MarketRegion marketRegion
    );
}
