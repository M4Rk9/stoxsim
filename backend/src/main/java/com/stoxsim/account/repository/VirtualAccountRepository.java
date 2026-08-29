package com.stoxsim.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.subscription.domain.SubscriptionPlan;

import jakarta.persistence.LockModeType;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.user.id = :userId
        """)
    List<VirtualAccount> findAllOwnedByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.user.id = :userId
          AND account.accountKind = com.stoxsim.account.domain.AccountKind.STANDARD
        ORDER BY account.marketRegion
        """)
    List<VirtualAccount> findAllByUserIdOrderByMarketRegion(@Param("userId") UUID userId);

    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.user.id = :userId
          AND account.marketRegion = :marketRegion
          AND account.accountKind = com.stoxsim.account.domain.AccountKind.STANDARD
        """)
    Optional<VirtualAccount> findByUserIdAndMarketRegion(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );

    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.id = :accountId
          AND account.user.id = :userId
        """)
    Optional<VirtualAccount> findOwnedById(
        @Param("userId") UUID userId,
        @Param("accountId") UUID accountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.user.id = :userId
          AND account.marketRegion = :marketRegion
          AND account.accountKind = com.stoxsim.account.domain.AccountKind.STANDARD
        """)
    Optional<VirtualAccount> findForUpdate(
        @Param("userId") UUID userId,
        @Param("marketRegion") MarketRegion marketRegion
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.id = :accountId
          AND account.user.id = :userId
        """)
    Optional<VirtualAccount> findOwnedForUpdate(
        @Param("userId") UUID userId,
        @Param("accountId") UUID accountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM VirtualAccount account WHERE account.id = :id")
    Optional<VirtualAccount> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT account
        FROM VirtualAccount account
        WHERE account.user.id = :userId
          AND account.accountKind = com.stoxsim.account.domain.AccountKind.SANDBOX
        ORDER BY account.sandboxPlan, account.sandboxSlot
        """)
    List<VirtualAccount> findSandboxesByUserId(@Param("userId") UUID userId);

    Optional<VirtualAccount> findByUserIdAndAccountKindAndSandboxPlanAndSandboxSlot(
        UUID userId,
        AccountKind accountKind,
        SubscriptionPlan sandboxPlan,
        int sandboxSlot
    );
}
