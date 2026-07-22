package com.stoxsim.portfolio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.domain.Holding;

import jakarta.persistence.LockModeType;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT holding
        FROM Holding holding
        WHERE holding.account.id = :accountId
          AND holding.instrument.id = :instrumentId
        """)
    Optional<Holding> findForUpdate(
        @Param("accountId") UUID accountId,
        @Param("instrumentId") UUID instrumentId
    );

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Holding> findAllByAccountUserIdAndAccountMarketRegionAndQuantityGreaterThanOrderByInstrumentTradingSymbol(
        UUID userId,
        MarketRegion marketRegion,
        long minimumQuantity
    );
}
