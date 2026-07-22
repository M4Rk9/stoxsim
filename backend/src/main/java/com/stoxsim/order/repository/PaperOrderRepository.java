package com.stoxsim.order.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.OrderStatus;
import com.stoxsim.order.domain.PaperOrder;

import jakarta.persistence.LockModeType;

public interface PaperOrderRepository extends JpaRepository<PaperOrder, UUID> {

    Optional<PaperOrder> findByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);

    List<PaperOrder> findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(
        UUID userId,
        MarketRegion marketRegion
    );

    Optional<PaperOrder> findByIdAndAccountUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT order
        FROM PaperOrder order
        JOIN FETCH order.account
        JOIN FETCH order.instrument
        WHERE order.id = :id
        """)
    Optional<PaperOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT order.id
        FROM PaperOrder order
        WHERE order.status = com.stoxsim.order.domain.OrderStatus.OPEN
          AND order.instrument.provider = :provider
          AND order.instrument.instrumentKey = :instrumentKey
          AND order.submittedForDate <= :tradeDate
        ORDER BY order.createdAt
        """)
    List<UUID> findOpenIdsForTick(
        @Param("provider") String provider,
        @Param("instrumentKey") String instrumentKey,
        @Param("tradeDate") LocalDate tradeDate
    );

    List<PaperOrder> findAllByStatus(OrderStatus status);

    @Query("""
        SELECT order.id
        FROM PaperOrder order
        WHERE order.status = com.stoxsim.order.domain.OrderStatus.OPEN
          AND order.submittedForDate <= :tradeDate
        """)
    List<UUID> findOpenIdsDueBy(@Param("tradeDate") LocalDate tradeDate);
}
