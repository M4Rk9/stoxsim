package com.stoxsim.progression.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "achievement_unlock")
public class AchievementUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "achievement_code", nullable = false, length = 64)
    private String achievementCode;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private Instant unlockedAt;

    protected AchievementUnlock() {
    }

    public AchievementUnlock(UUID userId, String achievementCode, Instant unlockedAt) {
        this.userId = userId;
        this.achievementCode = achievementCode;
        this.unlockedAt = unlockedAt;
    }

    public String getAchievementCode() {
        return achievementCode;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
