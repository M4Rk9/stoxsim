package com.stoxsim.progression.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.progression.domain.AchievementUnlock;

public interface AchievementUnlockRepository
    extends JpaRepository<AchievementUnlock, UUID> {

    List<AchievementUnlock> findAllByUserIdOrderByUnlockedAt(UUID userId);
}
