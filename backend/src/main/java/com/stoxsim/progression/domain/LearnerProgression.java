package com.stoxsim.progression.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learner_progression")
public class LearnerProgression {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_xp", nullable = false)
    private int totalXp;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "last_check_in_date")
    private LocalDate lastCheckInDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LearnerProgression() {
    }

    public LearnerProgression(UUID userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void awardXp(int amount, Instant now) {
        if (amount <= 0) {
            throw new IllegalArgumentException("XP award must be positive");
        }
        totalXp = Math.addExact(totalXp, amount);
        updatedAt = now;
    }

    public boolean checkIn(LocalDate date, Instant now) {
        if (lastCheckInDate != null && !date.isAfter(lastCheckInDate)) {
            return false;
        }
        currentStreak = lastCheckInDate != null
            && date.equals(lastCheckInDate.plusDays(1))
                ? currentStreak + 1
                : 1;
        longestStreak = Math.max(longestStreak, currentStreak);
        lastCheckInDate = date;
        updatedAt = now;
        return true;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getTotalXp() {
        return totalXp;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public LocalDate getLastCheckInDate() {
        return lastCheckInDate;
    }
}
