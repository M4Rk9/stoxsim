package com.stoxsim.progression.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LearnerProgressionTest {

    private static final Instant NOW = Instant.parse("2026-08-31T03:00:00Z");

    @Test
    void recordsOnlyOneCheckInPerDateAndBuildsConsecutiveStreaks() {
        LearnerProgression progression = new LearnerProgression(UUID.randomUUID(), NOW);

        assertThat(progression.checkIn(LocalDate.of(2026, 8, 29), NOW)).isTrue();
        assertThat(progression.checkIn(LocalDate.of(2026, 8, 29), NOW)).isFalse();
        assertThat(progression.checkIn(LocalDate.of(2026, 8, 30), NOW)).isTrue();
        assertThat(progression.checkIn(LocalDate.of(2026, 8, 31), NOW)).isTrue();

        assertThat(progression.getCurrentStreak()).isEqualTo(3);
        assertThat(progression.getLongestStreak()).isEqualTo(3);
    }

    @Test
    void resetsCurrentStreakAfterAGapWithoutReducingLongestStreak() {
        LearnerProgression progression = new LearnerProgression(UUID.randomUUID(), NOW);
        progression.checkIn(LocalDate.of(2026, 8, 27), NOW);
        progression.checkIn(LocalDate.of(2026, 8, 28), NOW);

        progression.checkIn(LocalDate.of(2026, 8, 31), NOW);

        assertThat(progression.getCurrentStreak()).isEqualTo(1);
        assertThat(progression.getLongestStreak()).isEqualTo(2);
    }
}
