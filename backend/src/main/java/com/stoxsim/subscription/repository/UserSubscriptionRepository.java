package com.stoxsim.subscription.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.subscription.domain.UserSubscription;

import jakarta.persistence.LockModeType;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    Optional<UserSubscription> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT subscription FROM UserSubscription subscription WHERE subscription.user.id = :userId")
    Optional<UserSubscription> findByUserIdForUpdate(@Param("userId") UUID userId);
}
