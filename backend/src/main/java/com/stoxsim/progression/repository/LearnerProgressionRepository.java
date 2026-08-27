package com.stoxsim.progression.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.progression.domain.LearnerProgression;

import jakarta.persistence.LockModeType;

public interface LearnerProgressionRepository
    extends JpaRepository<LearnerProgression, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO learner_progression (
            user_id, total_xp, current_streak, longest_streak, created_at, updated_at
        ) VALUES (:userId, 0, 0, 0, :now, :now)
        ON CONFLICT (user_id) DO NOTHING
        """, nativeQuery = true)
    void ensureExists(@Param("userId") UUID userId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT progression FROM LearnerProgression progression WHERE progression.userId = :userId")
    Optional<LearnerProgression> findByUserIdForUpdate(@Param("userId") UUID userId);
}
