package com.stoxsim.competition.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.competition.domain.CompetitionEntry;

import jakarta.persistence.LockModeType;

public interface CompetitionEntryRepository
    extends JpaRepository<CompetitionEntry, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO competition_entry (
            season_id, user_id, baseline_value, latest_value,
            return_percent, data_status, joined_at, valued_at
        ) VALUES (
            :seasonId, :userId, :baselineValue, :baselineValue,
            0, :dataStatus, :now, :now
        )
        ON CONFLICT (season_id, user_id) DO NOTHING
        """, nativeQuery = true)
    void ensureEntry(
        @Param("seasonId") UUID seasonId,
        @Param("userId") UUID userId,
        @Param("baselineValue") BigDecimal baselineValue,
        @Param("dataStatus") String dataStatus,
        @Param("now") Instant now
    );

    @EntityGraph(attributePaths = "user")
    Optional<CompetitionEntry> findBySeasonIdAndUserId(UUID seasonId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT entry
        FROM CompetitionEntry entry
        WHERE entry.season.id = :seasonId
          AND entry.user.id = :userId
        """)
    Optional<CompetitionEntry> findForUpdate(
        @Param("seasonId") UUID seasonId,
        @Param("userId") UUID userId
    );

    @EntityGraph(attributePaths = "user")
    List<CompetitionEntry> findAllBySeasonIdOrderByReturnPercentDescJoinedAtAsc(
        UUID seasonId,
        Pageable pageable
    );

    long countBySeasonId(UUID seasonId);

    long countBySeasonIdAndReturnPercentGreaterThan(
        UUID seasonId,
        BigDecimal returnPercent
    );

    @EntityGraph(attributePaths = "user")
    @Query("""
        SELECT entry
        FROM CompetitionEntry entry
        WHERE entry.season.id = :seasonId
          AND entry.user.id IN (
              SELECT member.user.id
              FROM LeagueMember member
              WHERE member.league.id = :leagueId
          )
        ORDER BY entry.returnPercent DESC, entry.joinedAt ASC
        """)
    List<CompetitionEntry> findLeagueStandings(
        @Param("seasonId") UUID seasonId,
        @Param("leagueId") UUID leagueId,
        Pageable pageable
    );
}
