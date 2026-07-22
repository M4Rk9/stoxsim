package com.stoxsim.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.market.domain.MarketRegion;

import jakarta.persistence.LockModeType;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    List<VirtualAccount> findAllByUserIdOrderByMarketRegion(UUID userId);

    Optional<VirtualAccount> findByUserIdAndMarketRegion(
        UUID userId,
        MarketRegion marketRegion
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.user.id = :userId
          AND account.marketRegion = :marketRegion
        """)
    Optional<VirtualAccount> findForUpdate(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM VirtualAccount account WHERE account.id = :id")
    Optional<VirtualAccount> findByIdForUpdate(@Param("id") UUID id);
}
