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
@Table(name = "mission_completion")
public class MissionCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "mission_code", nullable = false, length = 64)
    private String missionCode;

    @Column(name = "xp_awarded", nullable = false)
    private int xpAwarded;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected MissionCompletion() {
    }

    public MissionCompletion(
        UUID userId,
        String missionCode,
        int xpAwarded,
        Instant completedAt
    ) {
        this.userId = userId;
        this.missionCode = missionCode;
        this.xpAwarded = xpAwarded;
        this.completedAt = completedAt;
    }

    public String getMissionCode() {
        return missionCode;
    }

    public int getXpAwarded() {
        return xpAwarded;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
