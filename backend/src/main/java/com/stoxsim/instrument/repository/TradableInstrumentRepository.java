package com.stoxsim.instrument.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.market.domain.MarketRegion;

public interface TradableInstrumentRepository extends JpaRepository<TradableInstrument, UUID> {

    List<TradableInstrument> findAllByProviderAndInstrumentKeyIn(
        String provider,
        Collection<String> instrumentKeys
    );

    List<TradableInstrument> findAllByMarketRegionAndExchangeAndInstrumentTypeAndActiveTrueOrderByTradingSymbol(
        MarketRegion marketRegion,
        MarketExchange exchange,
        InstrumentType instrumentType
    );

    Optional<TradableInstrument> findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String tradingSymbol
    );

    @Query("""
        SELECT instrument
        FROM TradableInstrument instrument
        WHERE instrument.marketRegion = :marketRegion
          AND instrument.active = true
          AND (
              LOCATE(LOWER(:query), LOWER(instrument.tradingSymbol)) > 0
              OR LOCATE(LOWER(:query), LOWER(instrument.name)) > 0
              OR LOCATE(LOWER(:query), LOWER(COALESCE(instrument.isin, ''))) > 0
          )
        ORDER BY
          CASE WHEN LOWER(instrument.tradingSymbol) = LOWER(:query) THEN 0 ELSE 1 END,
          instrument.tradingSymbol
        """)
    List<TradableInstrument> search(
        @Param("marketRegion") MarketRegion marketRegion,
        @Param("query") String query,
        Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE TradableInstrument instrument
        SET instrument.active = false
        WHERE instrument.provider = :provider
          AND instrument.active = true
          AND (
              instrument.lastSeenSyncId IS NULL
              OR instrument.lastSeenSyncId <> :syncId
          )
        """)
    int deactivateMissing(
        @Param("provider") String provider,
        @Param("syncId") UUID syncId
    );
}
