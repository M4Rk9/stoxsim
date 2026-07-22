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

    @Query("""
        SELECT paperOrder
        FROM PaperOrder paperOrder
        WHERE paperOrder.account.user.id = :userId
          AND paperOrder.account.marketRegion = :marketRegion
        ORDER BY paperOrder.createdAt DESC
        """)
    List<PaperOrder> findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );

    @Query("""
        SELECT paperOrder
        FROM PaperOrder paperOrder
        WHERE paperOrder.id = :id
          AND paperOrder.account.user.id = :userId
        """)
    Optional<PaperOrder> findByIdAndAccountUserId(
        @Param("id") UUID id,
        @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT paperOrder
        FROM PaperOrder paperOrder
        JOIN FETCH paperOrder.account
        JOIN FETCH paperOrder.instrument
        WHERE paperOrder.id = :id
        """)
    Optional<PaperOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT paperOrder.id
        FROM PaperOrder paperOrder
        WHERE paperOrder.status = com.stoxsim.order.domain.OrderStatus.OPEN
          AND paperOrder.instrument.provider = :provider
          AND paperOrder.instrument.instrumentKey = :instrumentKey
          AND paperOrder.submittedForDate <= :tradeDate
        ORDER BY paperOrder.createdAt
        """)
    List<UUID> findOpenIdsForTick(
        @Param("provider") String provider,
        @Param("instrumentKey") String instrumentKey,
        @Param("tradeDate") LocalDate tradeDate
    );

    List<PaperOrder> findAllByStatus(OrderStatus status);

    @Query("""
        SELECT paperOrder.id
        FROM PaperOrder paperOrder
        WHERE paperOrder.status = com.stoxsim.order.domain.OrderStatus.OPEN
          AND paperOrder.submittedForDate <= :tradeDate
        """)
    List<UUID> findOpenIdsDueBy(@Param("tradeDate") LocalDate tradeDate);
}
